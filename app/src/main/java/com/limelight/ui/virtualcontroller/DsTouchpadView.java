package com.limelight.ui.virtualcontroller;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.limelight.binding.input.touch.MultitouchOffsetFix;

/**
 * A two-finger on-screen touch surface for the player-one virtual DS controller.
 */
public final class DsTouchpadView extends View {
    public interface Listener {
        void onTouchBegin(int pointerId, float normalizedX, float normalizedY);

        void onTouchMove(int pointerId, float normalizedX, float normalizedY);

        void onTouchEnd(int pointerId, float normalizedX, float normalizedY);

        void onCancelAll();

        void onTap();

        void onLongPressStart();

        void onLongPressEnd();
    }

    private static final int MAX_TOUCHES = 2;
    private static final int BUTTON_POINTER_ID = 0;
    private static final float DEFAULT_POSITION = 0.5f;
    private static final float CORNER_RADIUS_DP = 18f;
    private static final float INDICATOR_RADIUS_DP = 11f;
    private static final float TAP_DISTANCE_DP = 18f;
    private static final long EMPHASIS_FADE_DELAY_MS = 1200L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint primaryIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF drawingRect = new RectF();
    private final int[] androidPointerIds = new int[] { -1, -1 };
    private final boolean[] activePointers = new boolean[MAX_TOUCHES];
    private final float[] touchStartX = new float[MAX_TOUCHES];
    private final float[] touchStartY = new float[MAX_TOUCHES];
    private final float[] touchMaxDistance = new float[MAX_TOUCHES];
    private final long[] touchStartTimeMs = new long[MAX_TOUCHES];
    private final PointF[] pointerPositions = new PointF[] {
            new PointF(DEFAULT_POSITION, DEFAULT_POSITION),
            new PointF(DEFAULT_POSITION, DEFAULT_POSITION)
    };
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable fadeRunnable = () -> {
        visualEmphasis = false;
        invalidate();
    };
    private final Runnable longPressRunnable = this::triggerLongPress;

    private final float density;
    private final long longPressTimeoutMs;
    private Listener listener;
    private boolean visualEmphasis;
    private boolean longPressActive;

    public DsTouchpadView(Context context) {
        this(context, null);
    }

    public DsTouchpadView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DsTouchpadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        longPressTimeoutMs = ViewConfiguration.getLongPressTimeout();

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.5f * density);
        primaryIndicatorPaint.setColor(0xFF42C9E8);
        primaryIndicatorPaint.setStyle(Paint.Style.FILL);
        secondaryIndicatorPaint.setColor(0xFF8B82F6);
        secondaryIndicatorPaint.setStyle(Paint.Style.FILL);
        ringPaint.setColor(0x42FFFFFF);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(4f * density);

        setClickable(true);
        setFocusable(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Cancels all active controller touches and resets the local pointer state. */
    public void releaseTouches() {
        mainHandler.removeCallbacks(fadeRunnable);
        mainHandler.removeCallbacks(longPressRunnable);
        releaseLongPress();
        boolean hadActivePointers = hasActivePointers();
        for (int pointerId = 0; pointerId < MAX_TOUCHES; pointerId++) {
            clearPointer(pointerId);
        }
        visualEmphasis = false;
        if (hadActivePointers && listener != null) {
            listener.onCancelAll();
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) {
            return false;
        }

        // 三星 Game Booster 降分辨率时，若本手势被拆分到本 View 与画面 View，
        // 派发到这里的坐标会带固定偏移。这里统一换成已修正的事件副本。
        MotionEvent correctedEvent = MultitouchOffsetFix.obtainCorrectedEvent(this, event);
        if (correctedEvent != null) {
            try {
                return handleTouchEvent(correctedEvent);
            } finally {
                correctedEvent.recycle();
            }
        }
        return handleTouchEvent(event);
    }

    private boolean handleTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                requestParentNoIntercept();
                handlePointerDown(event, event.getActionIndex());
                return true;
            case MotionEvent.ACTION_MOVE:
                requestParentNoIntercept();
                handlePointerMove(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (handlePointerUp(event, event.getActionIndex())) {
                    performClick();
                    if (listener != null) {
                        listener.onTap();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseTouches();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawingRect.set(0f, 0f, getWidth(), getHeight());

        boolean active = hasActivePointers();
        int fillAlpha = active ? 0x2E : (visualEmphasis ? 0x19 : 0x09);
        int strokeAlpha = active ? 0x66 : (visualEmphasis ? 0x33 : 0x18);
        fillPaint.setColor((fillAlpha << 24) | 0x00FFFFFF);
        strokePaint.setColor((strokeAlpha << 24) | 0x00FFFFFF);

        float cornerRadius = CORNER_RADIUS_DP * density;
        canvas.drawRoundRect(drawingRect, cornerRadius, cornerRadius, fillPaint);
        canvas.drawRoundRect(drawingRect, cornerRadius, cornerRadius, strokePaint);

        float indicatorRadius = INDICATOR_RADIUS_DP * density;
        for (int pointerId = 0; pointerId < MAX_TOUCHES; pointerId++) {
            if (!activePointers[pointerId]) {
                continue;
            }
            PointF point = pointerPositions[pointerId];
            float x = point.x * getWidth();
            float y = point.y * getHeight();
            canvas.drawCircle(x, y, indicatorRadius * 1.2f, ringPaint);
            canvas.drawCircle(x, y, indicatorRadius,
                    pointerId == 0 ? primaryIndicatorPaint : secondaryIndicatorPaint);
        }
    }

    private void handlePointerDown(MotionEvent event, int pointerIndex) {
        int androidPointerId = event.getPointerId(pointerIndex);
        int pointerId = findPointerByAndroidId(androidPointerId);
        if (pointerId < 0) {
            pointerId = findFreePointer();
        }
        if (pointerId < 0) {
            return;
        }

        PointF point = pointerPositions[pointerId];
        point.set(normalizeX(event.getX(pointerIndex)), normalizeY(event.getY(pointerIndex)));
        androidPointerIds[pointerId] = androidPointerId;
        activePointers[pointerId] = true;
        touchStartX[pointerId] = event.getX(pointerIndex);
        touchStartY[pointerId] = event.getY(pointerIndex);
        touchMaxDistance[pointerId] = 0f;
        touchStartTimeMs[pointerId] = SystemClock.elapsedRealtime();
        revealEmphasis();
        if (listener != null) {
            listener.onTouchBegin(pointerId, point.x, point.y);
        }
        if (pointerId == BUTTON_POINTER_ID) {
            mainHandler.removeCallbacks(longPressRunnable);
            mainHandler.postDelayed(longPressRunnable, longPressTimeoutMs);
        }
        invalidate();
    }

    private void handlePointerMove(MotionEvent event) {
        for (int pointerIndex = 0; pointerIndex < event.getPointerCount(); pointerIndex++) {
            int pointerId = findPointerByAndroidId(event.getPointerId(pointerIndex));
            if (pointerId < 0) {
                continue;
            }

            PointF point = pointerPositions[pointerId];
            point.set(normalizeX(event.getX(pointerIndex)), normalizeY(event.getY(pointerIndex)));
            float dx = event.getX(pointerIndex) - touchStartX[pointerId];
            float dy = event.getY(pointerIndex) - touchStartY[pointerId];
            touchMaxDistance[pointerId] = Math.max(touchMaxDistance[pointerId],
                    (float) Math.hypot(dx, dy));
            if (pointerId == BUTTON_POINTER_ID && !longPressActive &&
                    touchMaxDistance[pointerId] >= TAP_DISTANCE_DP * density) {
                mainHandler.removeCallbacks(longPressRunnable);
            }
            revealEmphasis();
            if (listener != null) {
                listener.onTouchMove(pointerId, point.x, point.y);
            }
        }
        invalidate();
    }

    private boolean handlePointerUp(MotionEvent event, int pointerIndex) {
        int pointerId = findPointerByAndroidId(event.getPointerId(pointerIndex));
        if (pointerId < 0) {
            return false;
        }

        PointF point = pointerPositions[pointerId];
        point.set(normalizeX(event.getX(pointerIndex)), normalizeY(event.getY(pointerIndex)));
        float dx = event.getX(pointerIndex) - touchStartX[pointerId];
        float dy = event.getY(pointerIndex) - touchStartY[pointerId];
        touchMaxDistance[pointerId] = Math.max(touchMaxDistance[pointerId],
                (float) Math.hypot(dx, dy));
        if (listener != null) {
            listener.onTouchEnd(pointerId, point.x, point.y);
        }

        long durationMs = SystemClock.elapsedRealtime() - touchStartTimeMs[pointerId];
        boolean wasLongPress = pointerId == BUTTON_POINTER_ID && longPressActive;
        if (pointerId == BUTTON_POINTER_ID) {
            mainHandler.removeCallbacks(longPressRunnable);
            releaseLongPress();
        }
        boolean tap = pointerId == BUTTON_POINTER_ID && !wasLongPress &&
                touchMaxDistance[pointerId] < TAP_DISTANCE_DP * density &&
                durationMs < longPressTimeoutMs;
        clearPointer(pointerId);
        if (hasActivePointers()) {
            revealEmphasis();
        }
        else {
            scheduleFade();
        }
        invalidate();
        return tap;
    }

    private void triggerLongPress() {
        if (longPressActive || !activePointers[BUTTON_POINTER_ID] ||
                touchMaxDistance[BUTTON_POINTER_ID] >= TAP_DISTANCE_DP * density) {
            return;
        }

        longPressActive = true;
        if (listener != null) {
            listener.onLongPressStart();
        }
    }

    private void releaseLongPress() {
        if (!longPressActive) {
            return;
        }

        longPressActive = false;
        if (listener != null) {
            listener.onLongPressEnd();
        }
    }

    private void clearPointer(int pointerId) {
        activePointers[pointerId] = false;
        androidPointerIds[pointerId] = -1;
        pointerPositions[pointerId].set(DEFAULT_POSITION, DEFAULT_POSITION);
        touchMaxDistance[pointerId] = 0f;
        touchStartTimeMs[pointerId] = 0L;
    }

    private int findPointerByAndroidId(int androidPointerId) {
        for (int pointerId = 0; pointerId < MAX_TOUCHES; pointerId++) {
            if (androidPointerIds[pointerId] == androidPointerId) {
                return pointerId;
            }
        }
        return -1;
    }

    private int findFreePointer() {
        for (int pointerId = 0; pointerId < MAX_TOUCHES; pointerId++) {
            if (!activePointers[pointerId]) {
                return pointerId;
            }
        }
        return -1;
    }

    private void requestParentNoIntercept() {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private float normalizeX(float x) {
        return getWidth() > 0 ? clamp(x / getWidth()) : DEFAULT_POSITION;
    }

    private float normalizeY(float y) {
        return getHeight() > 0 ? clamp(y / getHeight()) : DEFAULT_POSITION;
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private boolean hasActivePointers() {
        for (boolean active : activePointers) {
            if (active) {
                return true;
            }
        }
        return false;
    }

    private void revealEmphasis() {
        mainHandler.removeCallbacks(fadeRunnable);
        visualEmphasis = true;
    }

    private void scheduleFade() {
        mainHandler.removeCallbacks(fadeRunnable);
        mainHandler.postDelayed(fadeRunnable, EMPHASIS_FADE_DELAY_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseTouches();
        mainHandler.removeCallbacks(fadeRunnable);
        mainHandler.removeCallbacks(longPressRunnable);
        super.onDetachedFromWindow();
    }
}
