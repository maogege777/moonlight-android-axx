package com.limelight.binding.input.touch;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.limelight.LimeLog;

/**
 * 三星多点触控坐标偏移修复（Game Booster 动态分辨率缩放）。
 *
 * 根因：三星会把被识别为「游戏」的 App 以低于屏幕真实分辨率的尺寸渲染（Game Booster 的
 * dynamic resolution scaling，即使从未打开 Game Booster Plus 也可能默认生效）。
 * 单指触摸时系统会把坐标换算回 App 坐标系，一切正常；但当一次多指手势被拆分派发到不同
 * View（例如一根手指在 StreamView 画面上、另一根在 OSC 虚拟按键的自定义 View 上）时，
 * 派发到每个 View 的坐标仍按缩放后的坐标系报告，于是出现固定偏移。
 *
 * 因此修复策略是：先测出缩放因子，并且只在「多指手势确实被拆分」时做逆缩放，
 * 其余场景（非三星、未降分辨率、单指、同 View 内多指）一律退化为原始坐标，零回归风险。
 *
 * 判定指纹（三个条件必须同时成立）：
 *   1. 0 < touchScale < 1（App 被报告的尺寸小于屏幕真实最大尺寸）
 *   2. 本次手势真实指针数 > 1
 *   3. 当前 View 收到的指针数 < 本次手势真实指针数（= 手势被拆分了 = bug 正在发生）
 *
 * 相关上游 issue（同类项目都遇到过，可作交叉验证）：
 *   moonlight-stream/moonlight-android #944
 *   Swordfish90/Lemuroid #178 / #320 / #558 / #1081
 *   EasyRPG/Player #2915
 *   issuetracker.google.com/issues/501144562
 *   F0RIS/SamsungMultitouchBugSample（最小复现 + Fix1 修复分支，本方案算法来源）
 *   Ghabry/easyrpg-player commit 2f8c13ba（该算法在生产项目中的落地实现）
 */
public final class MultitouchOffsetFix {

    // App 被报告的尺寸 / 屏幕真实最大尺寸。< 1.0 说明存在降分辨率。
    private static float touchScale = 1.0f;

    // 本次手势在 Activity 分发层看到的真实总指针数。
    private static int globalPointerCount = 0;

    private MultitouchOffsetFix() {
    }

    /**
     * 测量缩放因子。必须在 onCreate() 和 onConfigurationChanged() 都刷新
     * （旋转 / 尺寸变化后因子会变）。
     *
     * getMetrics() 给出的是缩放后报告给 App 的尺寸，getCurrentSizeRange() 给出真实最大尺寸，
     * 两者不一致即说明被降分辨率。等于 1 或异常值一律置为 1.0f（即不修正）。
     */
    @SuppressWarnings("deprecation")
    public static void updateTouchScale(Activity activity) {
        if (activity == null) {
            touchScale = 1.0f;
            return;
        }

        try {
            Display display = activity.getWindowManager().getDefaultDisplay();

            DisplayMetrics displayMetrics = new DisplayMetrics();
            Point smallestSize = new Point();
            Point largestSize = new Point();

            display.getMetrics(displayMetrics);
            display.getCurrentSizeRange(smallestSize, largestSize);

            int reportedMax = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            int realMax = Math.max(largestSize.x, largestSize.y);

            if (reportedMax <= 0 || realMax <= 0) {
                touchScale = 1.0f;
                return;
            }

            float scale = reportedMax / (float) realMax;
            touchScale = (scale > 0.0f && scale < 1.0f) ? scale : 1.0f;

            if (touchScale < 1.0f) {
                LimeLog.info("MultitouchOffsetFix: 检测到降分辨率，reported=" + reportedMax +
                        " real=" + realMax + " scale=" + touchScale);
            }
        } catch (Exception e) {
            // 任何异常都退化为不修正
            touchScale = 1.0f;
        }
    }

    public static float getTouchScale() {
        return touchScale;
    }

    /**
     * 在 Activity.dispatchTouchEvent() 里记录本次手势的真实总指针数。
     * 必须在事件继续向下分发之前调用。
     *
     * 分发层的 event.getPointerCount() 是手势的完整指针数；被拆分后派发到单个 View 的
     * 同名事件指针数会更少。两者的差就是「bug 正在发生」的判据。
     * 直接赋值（而不是取最大值）保证：当其他手指抬起、本手势退化为单指后，
     * 剩余手指的坐标不再被修正 —— 与「单指本来就正常」一致。
     */
    public static void setGlobalPointerCount(MotionEvent event) {
        if (event == null) {
            return;
        }

        // 只统计触摸屏手势：Activity.dispatchTouchEvent() 同样会收到手柄 / 摇杆事件，
        // 它们的 pointerCount 不是「手指数」，不能参与拆分判定。
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return;
        }

        globalPointerCount = event.getPointerCount();
    }

    public static int getGlobalPointerCount() {
        return globalPointerCount;
    }

    /**
     * 只有同时满足三个条件才修正。这个三重条件保证了非三星设备、未降分辨率的设备、
     * 单指操作、同 View 内多指操作全部退化为原始 event.getX()，行为与修复前完全一致。
     */
    public static boolean isCorrectionNeeded(MotionEvent event) {
        return touchScale > 0.0f && touchScale < 1.0f
                && globalPointerCount > 1
                && event != null
                && event.getPointerCount() < globalPointerCount;
    }

    // ---------------------------------------------------------------------------------------
    // 坐标入口
    //
    // 同时提供「View 相对」与「父绝对」两套入口，避免调用方自己加减 view.getX() 时
    // 漏掉或重复补偿。view 允许为 null（表示事件本来就在父布局坐标系里）。
    // ---------------------------------------------------------------------------------------

    /** 等价于原始 event.getX(i)，需要修正时已补偿。 */
    public static float getX(View view, MotionEvent event, int pointerIndex) {
        return getAbsoluteX(view, event, pointerIndex) - (view != null ? view.getX() : 0.0f);
    }

    /** 等价于原始 event.getY(i)，需要修正时已补偿。 */
    public static float getY(View view, MotionEvent event, int pointerIndex) {
        return getAbsoluteY(view, event, pointerIndex) - (view != null ? view.getY() : 0.0f);
    }

    /** 等价于 view.getX() + event.getX(i)，需要修正时已补偿。 */
    public static float getAbsoluteX(View view, MotionEvent event, int pointerIndex) {
        float x = event.getX(pointerIndex) + (view != null ? view.getX() : 0.0f);
        if (isCorrectionNeeded(event)) {
            x /= touchScale;
        }
        return x;
    }

    /** 等价于 view.getY() + event.getY(i)，需要修正时已补偿。 */
    public static float getAbsoluteY(View view, MotionEvent event, int pointerIndex) {
        float y = event.getY(pointerIndex) + (view != null ? view.getY() : 0.0f);
        if (isCorrectionNeeded(event)) {
            y /= touchScale;
        }
        return y;
    }

    /**
     * 历史坐标（ACTION_MOVE 常带多个历史采样点，必须一起修正，否则快速拖动会跳）。
     */
    public static float getHistoricalX(View view, MotionEvent event, int pointerIndex, int historyIndex) {
        return getAbsoluteHistoricalX(view, event, pointerIndex, historyIndex)
                - (view != null ? view.getX() : 0.0f);
    }

    public static float getHistoricalY(View view, MotionEvent event, int pointerIndex, int historyIndex) {
        return getAbsoluteHistoricalY(view, event, pointerIndex, historyIndex)
                - (view != null ? view.getY() : 0.0f);
    }

    public static float getAbsoluteHistoricalX(View view, MotionEvent event, int pointerIndex, int historyIndex) {
        float x = event.getHistoricalX(pointerIndex, historyIndex) + (view != null ? view.getX() : 0.0f);
        if (isCorrectionNeeded(event)) {
            x /= touchScale;
        }
        return x;
    }

    public static float getAbsoluteHistoricalY(View view, MotionEvent event, int pointerIndex, int historyIndex) {
        float y = event.getHistoricalY(pointerIndex, historyIndex) + (view != null ? view.getY() : 0.0f);
        if (isCorrectionNeeded(event)) {
            y /= touchScale;
        }
        return y;
    }

    /**
     * 接触面积 / 笔工具尺寸（getTouchMajor/Minor、getToolMajor/Minor）同样是坐标空间的量，
     * 容易漏但必须一起修。
     */
    public static float getSize(MotionEvent event, float size) {
        if (isCorrectionNeeded(event)) {
            size /= touchScale;
        }
        return size;
    }

    /**
     * 为自定义控件构造一个「已修正坐标」的事件副本，让所有子类一次性拿到正确坐标。
     * 不需要修正时返回 null（调用方直接使用原事件，不产生任何额外分配）。
     *
     * 采用 obtain(other) 完整拷贝 + transform(Matrix) 的方式，保留全部指针与历史采样点，
     * 因此对多指跟踪的控件（自由摇杆等）同样安全。矩阵组合与 getX()/getY() 的数学完全一致：
     *   corrected_view_relative = ((view_relative + view_origin) / scale) - view_origin
     *
     * 调用方必须 recycle() 返回的事件，否则泄漏。
     */
    public static MotionEvent obtainCorrectedEvent(View view, MotionEvent event) {
        if (!isCorrectionNeeded(event)) {
            return null;
        }

        MotionEvent correctedEvent = MotionEvent.obtain(event);

        final float inverseScale = 1.0f / touchScale;
        final float viewX = view != null ? view.getX() : 0.0f;
        final float viewY = view != null ? view.getY() : 0.0f;

        Matrix matrix = new Matrix();
        matrix.postTranslate(viewX, viewY);
        matrix.postScale(inverseScale, inverseScale);
        matrix.postTranslate(-viewX, -viewY);

        correctedEvent.transform(matrix);
        return correctedEvent;
    }
}
