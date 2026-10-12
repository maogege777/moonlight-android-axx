/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.touch.MultitouchOffsetFix;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AppDialog;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class VirtualControllerElement extends View {
    protected static boolean _PRINT_DEBUG_INFORMATION = false;

    public static final int EID_DPAD = 1;
    public static final int EID_LT = 2;
    public static final int EID_RT = 3;
    public static final int EID_LB = 4;
    public static final int EID_RB = 5;
    public static final int EID_A = 6;
    public static final int EID_B = 7;
    public static final int EID_X = 8;
    public static final int EID_Y = 9;
    public static final int EID_BACK = 10;
    public static final int EID_START = 11;
    public static final int EID_LS = 12;
    public static final int EID_RS = 13;
    public static final int EID_LSB = 14;
    public static final int EID_RSB = 15;
    public static final int EID_GDB = 17;
    //触控板
    public static final int EID_TOUCHPAD = 16;

    protected VirtualController virtualController;
    protected final int elementId;

    private final Paint paint = new Paint();

    protected int normalColor = 0xF0888888;
    protected int pressedColor = 0xF07272ED;
    private int configMoveColor = 0xF0FF0000;
    private int configResizeColor = 0xF0FF00FF;
    private int configSelectedColor = 0xF000FF00;

    private int configDisabledColor = 0xF0AAAAAA;
    protected int startSize_x;
    protected int startSize_y;

    float position_pressed_x = 0;
    float position_pressed_y = 0;

    public boolean enabled = true;

    private enum Mode {
        Normal,
        Resize,
        Move
    }

    private Mode currentMode = Mode.Normal;

    protected VirtualControllerElement(VirtualController controller, Context context, int elementId) {
        super(context);

        this.virtualController = controller;
        this.elementId = elementId;
        this.normalColor= PreferenceConfiguration.readPreferences(context).virtualkeyViewNormalColor;
    }

    protected void moveElement(int pressed_x, int pressed_y, int x, int y) {
        int newPos_x = (int) getX() + x - pressed_x;
        int newPos_y = (int) getY() + y - pressed_y;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = newPos_x > 0 ? newPos_x : 0;
        layoutParams.topMargin = newPos_y > 0 ? newPos_y : 0;
        layoutParams.rightMargin = 0;
        layoutParams.bottomMargin = 0;

        requestLayout();
    }

    protected void resizeElement(int pressed_x, int pressed_y, int width, int height) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        int newHeight = height + (startSize_y - pressed_y);
        int newWidth = width + (startSize_x - pressed_x);

        layoutParams.height = newHeight > 20 ? newHeight : 20;
        layoutParams.width = newWidth > 20 ? newWidth : 20;

        requestLayout();
    }

    protected  void actionDisableEnableButton(){
        enabled = !enabled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        onElementDraw(canvas);

        if (currentMode != Mode.Normal) {
            paint.setColor(configSelectedColor);
            paint.setStrokeWidth(getDefaultStrokeWidth());
            paint.setStyle(Paint.Style.STROKE);

            canvas.drawRect(paint.getStrokeWidth(), paint.getStrokeWidth(),
                    getWidth()-paint.getStrokeWidth(), getHeight()-paint.getStrokeWidth(),
                    paint);
        }

        super.onDraw(canvas);
    }

    /*
    protected void actionShowNormalColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog)
            {}

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                normalColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }

    protected void actionShowPressedColorChooser() {
        AmbilWarnaDialog colorDialog = new AmbilWarnaDialog(getContext(), normalColor, true, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                pressedColor = color;
                invalidate();
            }
        });
        colorDialog.show();
    }
    */

    protected void actionEnableMove() {
        currentMode = Mode.Move;
    }

    protected void actionEnableResize() {
        currentMode = Mode.Resize;
    }

    protected void actionCancel() {
        currentMode = Mode.Normal;
        invalidate();
    }

    protected int getDefaultColor() {
        if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
            return configMoveColor;
        else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
            return configResizeColor;
        else if (virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons)
            return enabled ? configSelectedColor: configDisabledColor;
        else return normalColor;
    }

    protected int getDefaultStrokeWidth() {
        DisplayMetrics screen = getResources().getDisplayMetrics();
        return (int)(screen.heightPixels*0.004f);
    }

    protected void showConfigurationDialog() {
        Activity activity = findActivity(getContext());
        if (activity == null) {
            return;
        }

        View content = inflate(activity, R.layout.dialog_virtual_controller_config, null);
        TextView moveView = content.findViewById(R.id.btn_virtual_controller_move);
        TextView resizeView = content.findViewById(R.id.btn_virtual_controller_resize);
        TextView cancelView = content.findViewById(R.id.btn_virtual_controller_cancel);

        android.app.AlertDialog dialog = AppDialog.createCustomDialog(activity, content, true);
        if (dialog == null) {
            return;
        }
        moveView.setOnClickListener(v -> {
            dialog.dismiss();
            actionEnableMove();
        });
        resizeView.setOnClickListener(v -> {
            dialog.dismiss();
            actionEnableResize();
        });
        cancelView.setOnClickListener(v -> {
            dialog.dismiss();
            actionCancel();
        });
        AppDialog.showCustomDialog(activity, dialog, 0.68f, 360,
                moveView, cancelView, moveView, resizeView, cancelView);
    }

    private static Activity findActivity(Context context) {
        Context currentContext = context;
        while (currentContext instanceof ContextWrapper) {
            if (currentContext instanceof Activity) {
                return (Activity) currentContext;
            }
            currentContext = ((ContextWrapper) currentContext).getBaseContext();
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ignore secondary touches on controls
        //
        // NB: We can get an additional pointer down if the user touches a non-StreamView area
        // while also touching an OSC control, even if that pointer down doesn't correspond to
        // an area of the OSC control.
        if (event.getActionIndex() != 0) {
            return true;
        }

        if (virtualController.getControllerMode() == VirtualController.ControllerMode.Active) {
            // 三星 Game Booster 降分辨率时，跨 View 的多指手势会被派发到本控件的坐标按缩放后的
            // 坐标系报告。这里在基类一次性构造「已修正坐标」的事件副本，所有子类（DigitalButton /
            // AnalogStick / DigitalPad 等）自动拿到正确坐标，无需逐个改子类。
            MotionEvent correctedEvent = MultitouchOffsetFix.obtainCorrectedEvent(this, event);
            if (correctedEvent != null) {
                try {
                    return onElementTouchEvent(correctedEvent);
                } finally {
                    // obtain 出来的事件必须 recycle，否则泄漏
                    correctedEvent.recycle();
                }
            }
            return onElementTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // 配置模式（拖动 / 缩放按钮）的按下坐标同样要走修正入口，
                // 否则在三星上拖按钮会跳。
                position_pressed_x = MultitouchOffsetFix.getX(this, event, 0);
                position_pressed_y = MultitouchOffsetFix.getY(this, event, 0);
                startSize_x = getWidth();
                startSize_y = getHeight();

                if (virtualController.getControllerMode() == VirtualController.ControllerMode.MoveButtons)
                    actionEnableMove();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.ResizeButtons)
                    actionEnableResize();
                else if (virtualController.getControllerMode() == VirtualController.ControllerMode.DisableEnableButtons)
                    actionDisableEnableButton();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float moveX = MultitouchOffsetFix.getX(this, event, 0);
                float moveY = MultitouchOffsetFix.getY(this, event, 0);

                switch (currentMode) {
                    case Move: {
                        moveElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) moveX,
                                (int) moveY);
                        break;
                    }
                    case Resize: {
                        resizeElement(
                                (int) position_pressed_x,
                                (int) position_pressed_y,
                                (int) moveX,
                                (int) moveY);
                        break;
                    }
                    case Normal: {
                        break;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                actionCancel();
                return true;
            }
            default: {
            }
        }
        return true;
    }

    abstract protected void onElementDraw(Canvas canvas);

    abstract public boolean onElementTouchEvent(MotionEvent event);

    protected static final void _DBG(String text) {
        if (_PRINT_DEBUG_INFORMATION) {
//            System.out.println(text);
        }
    }

    public void setColors(int normalColor, int pressedColor) {
        this.normalColor = normalColor;
        this.pressedColor = pressedColor;

        invalidate();
    }


    public void setOpacity(int opacity) {
        int hexOpacity = opacity * 255 / 100;
        this.normalColor = (hexOpacity << 24) | (normalColor & 0x00FFFFFF);
        this.pressedColor = (hexOpacity << 24) | (pressedColor & 0x00FFFFFF);

        invalidate();
    }

    protected final float getPercent(float value, float percent) {
        return value / 100 * percent;
    }

    protected final int getCorrectWidth() {
        return getWidth() > getHeight() ? getHeight() : getWidth();
    }


    public JSONObject getConfiguration() throws JSONException {
        JSONObject configuration = new JSONObject();

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        configuration.put("LEFT", layoutParams.leftMargin);
        configuration.put("TOP", layoutParams.topMargin);
        configuration.put("WIDTH", layoutParams.width);
        configuration.put("HEIGHT", layoutParams.height);
        configuration.put("ENABLED", enabled);
        return configuration;
    }

    public void loadConfiguration(JSONObject configuration) throws JSONException {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();

        layoutParams.leftMargin = configuration.getInt("LEFT");
        layoutParams.topMargin = configuration.getInt("TOP");
        layoutParams.width = configuration.getInt("WIDTH");
        layoutParams.height = configuration.getInt("HEIGHT");
        enabled = configuration.getBoolean("ENABLED");
        setVisibility(enabled ? VISIBLE: GONE);
        requestLayout();
    }


    public void zoomViewWidthHeight(float scaleFactor){
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) getLayoutParams();
        layoutParams.width = (int) (layoutParams.width*scaleFactor);
        layoutParams.height = (int) (layoutParams.height*scaleFactor);
        requestLayout();
    }
}
