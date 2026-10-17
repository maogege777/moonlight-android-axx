package com.limelight.ui.gamemenu;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW]
 *
 * 虚拟手柄 / 虚拟按键 按钮右上角的「布局方案」角标。
 * 视觉：实心圆角矩形底 + 空心（描边）数字，数字 1~5 对应方案 1~5。
 *
 * 用自绘而不是 TextView，是因为空心数字需要 Paint.Style.STROKE，TextView 无法直接表达；
 * 自绘也能保证 15dp 小尺寸下的居中与描边粗细。
 *
 * 触摸说明：本控件不可点击、不可获得焦点，落在它范围内的触摸会继续下发给下层的 Button，
 * 因此角标不会在按钮右上角形成点击死角。
 */
public class SchemeBadgeView extends View {

    /** 圆角半径（dp） */
    private static final float CORNER_RADIUS_DP = 3f;
    /** 字号相对控件高度的比例 */
    private static final float TEXT_SIZE_RATIO = 0.76f;
    /** 描边宽度相对字号的比例 */
    private static final float STROKE_RATIO = 0.09f;

    /** 未启用时的实心底色（近黑，压在深灰按钮上也清晰） */
    private static final int COLOR_BG_IDLE = 0xE6171719;
    /** 启用时的实心底色（深绿，与亮绿按钮形成层次而不糊成一片） */
    private static final int COLOR_BG_ACTIVE = 0xE6143A02;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String text = "1";
    private boolean active = true;

    public SchemeBadgeView(Context context) {
        this(context, null);
    }

    public SchemeBadgeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SchemeBadgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        fillPaint.setStyle(Paint.Style.FILL);

        textPaint.setStyle(Paint.Style.STROKE);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);

        setClickable(false);
        setFocusable(false);

        applyColors();
    }

    private void applyColors() {
        fillPaint.setColor(active ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
    }

    /** 设置显示的数字（1~5），对应方案序号。 */
    public void setSchemeNumber(int number) {
        String value = String.valueOf(number);
        if (!value.equals(text)) {
            text = value;
            invalidate();
        }
    }

    /** 跟随对应虚拟手柄 / 虚拟按键的启用状态切换底色。 */
    public void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            applyColors();
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final float w = getWidth();
        final float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        final float radius = dp(CORNER_RADIUS_DP);
        rect.set(0, 0, w, h);
        canvas.drawRoundRect(rect, radius, radius, fillPaint);

        final float textSize = h * TEXT_SIZE_RATIO;
        textPaint.setTextSize(textSize);
        textPaint.setStrokeWidth(Math.max(dp(0.7f), textSize * STROKE_RATIO));

        // 以控件高度垂直居中（补偿字体 ascent/descent 造成的上下不对称）
        final Paint.FontMetrics fm = textPaint.getFontMetrics();
        final float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, w / 2f, baseline, textPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
