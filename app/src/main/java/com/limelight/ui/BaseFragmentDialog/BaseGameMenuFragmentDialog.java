package com.limelight.ui.BaseFragmentDialog;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.limelight.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;


public abstract class BaseGameMenuFragmentDialog extends DialogFragment {

    private static final String TAG = "base_bottom_dialog";

    private static final float DEFAULT_DIM = 0.3f;
    private static final List<WeakReference<BaseGameMenuFragmentDialog>> ACTIVE_DIALOGS =
            new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.BottomDialog);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
//
//        // If we're going to use immersive mode, we want to have
//        // the entire screen
        getDialog().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
//
        getDialog().setCanceledOnTouchOutside(getCancelOutside());

        View v = inflater.inflate(getLayoutRes(), container, false);
        bindView(v);
        return v;
    }

    @LayoutRes
    public abstract int getLayoutRes();

    public abstract void bindView(View v);

    @Override
    public void onStart() {
        super.onStart();

        synchronized (ACTIVE_DIALOGS) {
            removeInactiveDialogs(this);
            ACTIVE_DIALOGS.add(new WeakReference<>(this));
        }

        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();

        params.dimAmount = getDimAmount();

        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.height = WindowManager.LayoutParams.MATCH_PARENT;
            if (getViewSize() > 0) {
                params.width = getViewSize();
            } else {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            }
            params.gravity = Gravity.END;
        }else{
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            int height=getActivity().getResources().getDisplayMetrics().heightPixels*2/3;
            if(height>0){
                params.height = height;
            }else{
                if (getViewSize() > 0) {
                    params.height = getViewSize();
                } else {
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                }
            }
            params.gravity = Gravity.BOTTOM;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            // 设置为 SHORT_EDGES，允许内容延伸进刘海
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        window.setAttributes(params);

//        getDialog().getWindow().getDecorView().setSystemUiVisibility(
//                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                        | View.SYSTEM_UI_FLAG_FULLSCREEN
//                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//        );
    }

    @Override
    public void onStop() {
        synchronized (ACTIVE_DIALOGS) {
            removeInactiveDialogs(this);
        }
        super.onStop();
    }

    public static boolean dispatchKeyEventToTopDialog(KeyEvent event) {
        synchronized (ACTIVE_DIALOGS) {
            for (int i = ACTIVE_DIALOGS.size() - 1; i >= 0; i--) {
                BaseGameMenuFragmentDialog fragment = ACTIVE_DIALOGS.get(i).get();
                if (fragment == null || fragment.getDialog() == null ||
                        !fragment.getDialog().isShowing()) {
                    ACTIVE_DIALOGS.remove(i);
                    continue;
                }
                return fragment.getDialog().dispatchKeyEvent(event);
            }
        }
        return false;
    }

    /**
     * 关闭当前全部串流菜单弹窗（EXT-QUICK-KEYS-IN-OPERATIONS v4）：
     * 「操作」面板里的自定义快捷键单击发送后，连同下层主菜单一起关闭，
     * 而不是只关闭一层回到主菜单。
     */
    public static void dismissAllDialogs() {
        List<BaseGameMenuFragmentDialog> targets = new ArrayList<>();
        synchronized (ACTIVE_DIALOGS) {
            for (WeakReference<BaseGameMenuFragmentDialog> ref : ACTIVE_DIALOGS) {
                BaseGameMenuFragmentDialog fragment = ref.get();
                if (fragment != null && fragment.getDialog() != null &&
                        fragment.getDialog().isShowing()) {
                    targets.add(fragment);
                }
            }
            ACTIVE_DIALOGS.clear();
        }
        for (BaseGameMenuFragmentDialog fragment : targets) {
            try {
                fragment.dismiss();
            } catch (IllegalStateException ignored) {
                // 已处于状态保存之后，dismiss 不可用；此时窗口层由系统处理
            }
        }
    }

    private static void removeInactiveDialogs(BaseGameMenuFragmentDialog target) {
        for (int i = ACTIVE_DIALOGS.size() - 1; i >= 0; i--) {
            BaseGameMenuFragmentDialog fragment = ACTIVE_DIALOGS.get(i).get();
            if (fragment == null || fragment == target || fragment.getDialog() == null ||
                    !fragment.getDialog().isShowing()) {
                ACTIVE_DIALOGS.remove(i);
            }
        }
    }

    public int getViewSize() {
        return -1;
    }

    public float getDimAmount() {
        return DEFAULT_DIM;
    }

    public boolean getCancelOutside() {
        return true;
    }

    public String getFragmentTag() {
        return TAG;
    }

    public void show(FragmentManager fragmentManager) {
        show(fragmentManager, getFragmentTag());
    }
}
