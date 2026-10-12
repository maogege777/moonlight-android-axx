package com.limelight.extensions.input.touch;

import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import com.limelight.binding.input.touch.MultitouchOffsetFix;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.video.VideoZoomController;

/**
 * EXTENSION DEVELOPMENT [EXT-TOUCHPAD-MULTI-GESTURE] [ADDED]
 *
 * Android adapter and remote-input sink for the dedicated multi-gesture touchpad mode.
 */
public final class MultiGestureTouchpadExtensionController
        implements MultiGestureTouchContext.Listener {
    public static final int MODE_ID = 7;

    private static final int SCROLL_SPEED_FACTOR = 5;
    private static final long TAP_TIMEOUT_MS = 250;
    private static final long BUTTON_UP_DELAY_MS = 100;
    private static final float PINCH_ACTIVATION_DP = 12f;
    private static final float PINCH_ACTIVATION_RATIO = 0.05f;
    private static final float EDGE_PAN_INSET_DP = 1f;
    private static final float MIN_EDGE_PAN_SCALE = 1.001f;

    private final NvConnection conn;
    private final View targetView;
    private final View coordinateView;
    private final PreferenceConfiguration prefConfig;
    private final VideoZoomController videoZoomController;
    private final MultiGestureTouchContext gestureContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float[] pointerX = new float[2];
    private final float[] pointerY = new float[2];
    private final int[] sourceLocation = new int[2];
    private final int[] coordinateLocation = new int[2];
    private final PointF trackedCursorPoint = new PointF();
    private final PointF transformedContentStart = new PointF();
    private final PointF transformedContentEnd = new PointF();
    private final float[] edgePanViewport = new float[4];
    private final float[] edgePanDelta = new float[2];
    private final MultiGestureCursorTracker cursorTracker = new MultiGestureCursorTracker();
    private final float edgePanInsetPx;
    private View currentSourceView;
    private boolean ignoreUntilNextDown;
    private boolean leftButtonDown;
    private boolean rightButtonDown;
    private boolean cursorSynchronized;
    private float moveRemainderX;
    private float moveRemainderY;
    private float scrollRemainder;
    private final Runnable leftButtonUp = () -> releaseButton(MouseButtonPacket.BUTTON_LEFT);
    private final Runnable rightButtonUp = () -> releaseButton(MouseButtonPacket.BUTTON_RIGHT);

    public MultiGestureTouchpadExtensionController(Context context,
                                                   NvConnection conn,
                                                   View targetView,
                                                   View coordinateView,
                                                   PreferenceConfiguration prefConfig,
                                                   VideoZoomController videoZoomController) {
        this.conn = conn;
        this.targetView = targetView;
        this.coordinateView = coordinateView;
        this.prefConfig = prefConfig;
        this.videoZoomController = videoZoomController;

        float density = context.getResources().getDisplayMetrics().density;
        edgePanInsetPx = EDGE_PAN_INSET_DP * density;
        float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gestureContext = new MultiGestureTouchContext(
                this,
                touchSlop,
                touchSlop,
                PINCH_ACTIVATION_DP * density,
                PINCH_ACTIVATION_RATIO,
                TAP_TIMEOUT_MS);
    }

    public boolean onTouchEvent(View sourceView, MotionEvent event) {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return false;
        }

        int action = event.getActionMasked();
        if (ignoreUntilNextDown) {
            if (action != MotionEvent.ACTION_DOWN) {
                return false;
            }
            ignoreUntilNextDown = false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            resetMotionRemainders();
            synchronizeCursorIfNeeded();
        }

        currentSourceView = sourceView != null ? sourceView : coordinateView;
        int coordinateCount = Math.min(event.getPointerCount(), 2);
        for (int i = 0; i < coordinateCount; i++) {
            // 三星 Game Booster 降分辨率时，被拆分派发到本 View 的坐标仍按缩放后的坐标系报告
            pointerX[i] = MultitouchOffsetFix.getX(currentSourceView, event, i);
            pointerY[i] = MultitouchOffsetFix.getY(currentSourceView, event, i);
        }
        return gestureContext.onTouchEvent(
                action,
                event.getActionIndex(),
                event.getPointerCount(),
                pointerX,
                pointerY,
                event.getEventTime());
    }

    /** Lets the existing multi-finger keyboard shortcut own the rest of the current gesture. */
    public void cancelUntilNextGesture() {
        gestureContext.cancel();
        ignoreUntilNextDown = true;
        resetMotionRemainders();
    }

    public void destroy() {
        gestureContext.cancel();
        handler.removeCallbacks(leftButtonUp);
        handler.removeCallbacks(rightButtonUp);
        releaseButton(MouseButtonPacket.BUTTON_LEFT);
        releaseButton(MouseButtonPacket.BUTTON_RIGHT);
        currentSourceView = null;
    }

    @Override
    public void onPointerMove(float deltaX, float deltaY) {
        int targetWidth = Math.max(targetView.getWidth(), 1);
        int targetHeight = Math.max(targetView.getHeight(), 1);
        View sourceView = currentSourceView != null ? currentSourceView : coordinateView;
        // MotionEvent coordinates are inverse-transformed into a scaled child. Restore physical
        // finger travel so mouse sensitivity remains stable at every video zoom level.
        float scaledX = deltaX * sourceView.getScaleX()
                * MultiGestureCursorTracker.REFERENCE_WIDTH / targetWidth;
        float scaledY = deltaY * sourceView.getScaleY()
                * MultiGestureCursorTracker.REFERENCE_HEIGHT / targetHeight;

        scaledX *= prefConfig.mouseTouchPadSensitityX * 0.01f;
        scaledY *= prefConfig.mouseTouchPadSensitityY * 0.01f;

        int outputX = consumeRoundedX(scaledX);
        int outputY = consumeRoundedY(scaledY);
        if (outputX == 0 && outputY == 0) {
            return;
        }

        synchronizeCursorIfNeeded();
        cursorTracker.move(clampToShort(outputX), clampToShort(outputY));
        sendTrackedCursorPosition();
        panForTrackedCursor();
    }

    @Override
    public void onVerticalScroll(float deltaY) {
        int targetHeight = Math.max(targetView.getHeight(), 1);
        View sourceView = currentSourceView != null ? currentSourceView : coordinateView;
        float scaledDelta = deltaY * sourceView.getScaleY()
                * MultiGestureCursorTracker.REFERENCE_HEIGHT / targetHeight
                * SCROLL_SPEED_FACTOR + scrollRemainder;
        int output = Math.round(scaledDelta);
        scrollRemainder = scaledDelta - output;
        if (output != 0) {
            conn.sendMouseHighResScroll(clampToShort(output));
        }
    }

    @Override
    public void onZoom(float scaleFactor, float focusX, float focusY) {
        if (videoZoomController == null) {
            return;
        }

        View sourceView = currentSourceView != null ? currentSourceView : coordinateView;
        sourceView.getLocationInWindow(sourceLocation);
        coordinateView.getLocationInWindow(coordinateLocation);
        // VideoZoomController uses a zero pivot. Touch coordinates delivered to a transformed
        // child are local/unscaled, so restore the child's visual scale before converting them
        // into the controller's coordinate view.
        float coordinateFocusX = focusX * sourceView.getScaleX()
                + sourceLocation[0] - coordinateLocation[0];
        float coordinateFocusY = focusY * sourceView.getScaleY()
                + sourceLocation[1] - coordinateLocation[1];
        videoZoomController.scaleBy(scaleFactor, coordinateFocusX, coordinateFocusY);
    }

    @Override
    public void onLeftClick() {
        clickButton(MouseButtonPacket.BUTTON_LEFT);
    }

    @Override
    public void onRightClick() {
        clickButton(MouseButtonPacket.BUTTON_RIGHT);
    }

    private int consumeRoundedX(float delta) {
        float value = delta + moveRemainderX;
        int output = Math.round(value);
        moveRemainderX = value - output;
        return output;
    }

    private int consumeRoundedY(float delta) {
        float value = delta + moveRemainderY;
        int output = Math.round(value);
        moveRemainderY = value - output;
        return output;
    }

    private void resetMotionRemainders() {
        moveRemainderX = 0f;
        moveRemainderY = 0f;
        scrollRemainder = 0f;
    }

    private void panForTrackedCursor() {
        if (videoZoomController == null
                || videoZoomController.getScale() <= MIN_EDGE_PAN_SCALE
                || !videoZoomController.mapNormalizedVideoToPoint(
                        cursorTracker.getNormalizedX(), cursorTracker.getNormalizedY(),
                        trackedCursorPoint)
                || !videoZoomController.mapNormalizedVideoToPoint(
                        0f, 0f, transformedContentStart)
                || !videoZoomController.mapNormalizedVideoToPoint(
                        1f, 1f, transformedContentEnd)) {
            return;
        }

        // Zoomed content may extend beyond the original video rectangle. Use the full gesture
        // coordinate viewport as the trigger aperture, while preserving any bottom area reserved
        // by the IME accessory extension through the video view's layout margin.
        MultiGestureViewportCalculator.calculate(
                coordinateView.getWidth(),
                coordinateView.getHeight(),
                getReservedBottomInset(),
                edgePanViewport);
        MultiGestureEdgePanCalculator.calculate(
                trackedCursorPoint.x,
                trackedCursorPoint.y,
                edgePanViewport[MultiGestureViewportCalculator.LEFT],
                edgePanViewport[MultiGestureViewportCalculator.TOP],
                edgePanViewport[MultiGestureViewportCalculator.RIGHT],
                edgePanViewport[MultiGestureViewportCalculator.BOTTOM],
                transformedContentStart.x,
                transformedContentStart.y,
                transformedContentEnd.x,
                transformedContentEnd.y,
                edgePanInsetPx,
                edgePanDelta);
        if (edgePanDelta[0] != 0f || edgePanDelta[1] != 0f) {
            videoZoomController.panBy(edgePanDelta[0], edgePanDelta[1]);
        }
    }

    private int getReservedBottomInset() {
        if (targetView.getParent() != coordinateView) {
            return 0;
        }

        ViewGroup.LayoutParams rawLayoutParams = targetView.getLayoutParams();
        if (!(rawLayoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return 0;
        }

        return Math.max(0, ((ViewGroup.MarginLayoutParams) rawLayoutParams).bottomMargin);
    }

    private void synchronizeCursorIfNeeded() {
        if (cursorSynchronized) {
            return;
        }
        sendTrackedCursorPosition();
        cursorSynchronized = true;
    }

    private void sendTrackedCursorPosition() {
        conn.sendMousePosition(
                cursorTracker.getReferenceX(),
                cursorTracker.getReferenceY(),
                MultiGestureCursorTracker.REFERENCE_WIDTH,
                MultiGestureCursorTracker.REFERENCE_HEIGHT);
    }

    private void clickButton(byte button) {
        Runnable buttonUp;
        if (button == MouseButtonPacket.BUTTON_LEFT) {
            handler.removeCallbacks(leftButtonUp);
            leftButtonDown = true;
            buttonUp = leftButtonUp;
        }
        else {
            handler.removeCallbacks(rightButtonUp);
            rightButtonDown = true;
            buttonUp = rightButtonUp;
        }
        conn.sendMouseButtonDown(button);
        handler.postDelayed(buttonUp, BUTTON_UP_DELAY_MS);
    }

    private void releaseButton(byte button) {
        if (button == MouseButtonPacket.BUTTON_LEFT) {
            if (!leftButtonDown) {
                return;
            }
            leftButtonDown = false;
        }
        else {
            if (!rightButtonDown) {
                return;
            }
            rightButtonDown = false;
        }
        conn.sendMouseButtonUp(button);
    }

    private static short clampToShort(int value) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

}
