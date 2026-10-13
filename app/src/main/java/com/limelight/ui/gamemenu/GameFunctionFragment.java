package com.limelight.ui.gamemenu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;

/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameFunctionFragment extends BaseGameMenuDialog implements View.OnClickListener {
    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu_function;
    }

    private ImageButton ibtn_back;
    private TextView tx_title;

    private String title;

    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
    // 麦克风 / 虚拟鼠标 / 手柄键鼠 开关按钮从主菜单移入本面板，
    // 需要持有 Game / NvConnection 以保留原有的状态高亮与按键发送逻辑。
    private Button btn_mic;
    private Button btn_virtual_mouse;
    private Button btn_gamepad_mouse;

    private Game game;
    private NvConnection conn;
    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

    @Override
    public void bindView(View v) {
        super.bindView(v);
        ibtn_back=v.findViewById(R.id.ibtn_back);
        tx_title=v.findViewById(R.id.tx_title);

        if(!TextUtils.isEmpty(title)){
            tx_title.setText(title);
        }
        ibtn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        v.findViewById(R.id.btn_task_manager).setOnClickListener(this);
        v.findViewById(R.id.btn_sleep).setOnClickListener(this);
        v.findViewById(R.id.btn_shutdown).setOnClickListener(this);
        v.findViewById(R.id.btn_clipboard_send).setOnClickListener(this);
        v.findViewById(R.id.btn_reboot).setOnClickListener(this);
        v.findViewById(R.id.btn_open_setting).setOnClickListener(this);
        v.findViewById(R.id.btn_logout).setOnClickListener(this);
        v.findViewById(R.id.btn_clipboard_open).setOnClickListener(this);
        v.findViewById(R.id.btn_computer).setOnClickListener(this);
        v.findViewById(R.id.btn_win_center).setOnClickListener(this);
        v.findViewById(R.id.btn_win_p).setOnClickListener(this);
        v.findViewById(R.id.btn_hdr).setOnClickListener(this);

        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 显示器1~4 已移出本面板（显示器1~3 上移至主菜单，显示器4 隐藏注释），不再绑定点击。
        // 移入的开关按钮在此绑定并初始化状态高亮。
        btn_mic=v.findViewById(R.id.btn_mic);
        btn_mic.setOnClickListener(this);
        btn_virtual_mouse=v.findViewById(R.id.btn_virtual_mouse);
        btn_virtual_mouse.setOnClickListener(this);
        btn_gamepad_mouse=v.findViewById(R.id.btn_gamepad_mouse);
        btn_gamepad_mouse.setOnClickListener(this);
        if(game!=null){
            refreshMicButton();
            refreshVirtualMouseButton();
            refreshGamepadMouseButton();
        }
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END
    }

    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
    private void refreshMicButton() {
        if (btn_mic != null && game != null) {
            btn_mic.setBackgroundResource(game.micStatus == 0?R.drawable.ic_game_menu_btn_selector:R.drawable.ic_game_menu_btn_green_selector);
        }
    }

    private void refreshVirtualMouseButton() {
        if (btn_virtual_mouse != null && game != null) {
            btn_virtual_mouse.setBackgroundResource(game.isVirtualMouseEnabled()
                    ? R.drawable.ic_game_menu_btn_green_selector
                    : R.drawable.ic_game_menu_btn_selector);
            btn_virtual_mouse.setText(game.isVirtualMouseEnabled()
                    ? R.string.game_menu_disable_virtual_mouse
                    : R.string.game_menu_enable_virtual_mouse);
        }
    }

    private void refreshGamepadMouseButton() {
        if (btn_gamepad_mouse != null && game != null) {
            btn_gamepad_mouse.setBackgroundResource(game.isGamepadMouseModeEnabled()
                    ? R.drawable.ic_game_menu_btn_green_selector
                    : R.drawable.ic_game_menu_btn_selector);
        }
    }
    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

    public void setTitle(String title) {
        this.title = title;
    }

    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
    public void setGame(Game game) {
        this.game = game;
    }

    public void setConn(NvConnection conn) {
        this.conn = conn;
    }
    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

    private onClick click;

    @Override
    public void onClick(View v) {
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 移入的开关按钮：点击后即时切换状态并刷新高亮，不关闭面板。
        if (v.getId() == R.id.btn_mic) {
            if (game != null) {
                game.switchMic();
                refreshMicButton();
                btn_mic.postDelayed(this::refreshMicButton, 400);
                btn_mic.postDelayed(this::refreshMicButton, 1200);
            }
            return;
        }

        if (v.getId() == R.id.btn_virtual_mouse) {
            if (game != null) {
                game.toggleVirtualMouse();
                refreshVirtualMouseButton();
                btn_virtual_mouse.postDelayed(this::refreshVirtualMouseButton, 400);
            }
            return;
        }

        if (v.getId() == R.id.btn_gamepad_mouse) {
            if (game != null) {
                game.toggleGamepadMouseMode();
                refreshGamepadMouseButton();
            }
            return;
        }
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

        if(click==null){
            return;
        }
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 注销 / 关机 / 睡眠 / 重启 移至本组最后，点击后弹出「确定执行吗？」二次确认，
        // 确定才发送按键，取消仅关闭提示。
        if(v.getId()==R.id.btn_logout){
            confirmAction("注销", () -> click.click("注销",0));
            return;
        }
        if(v.getId()==R.id.btn_shutdown){
            confirmAction("关机", () -> click.click("关机",1));
            return;
        }
        if(v.getId()==R.id.btn_sleep){
            confirmAction("睡眠", () -> click.click("睡眠",2));
            return;
        }
        if(v.getId()==R.id.btn_reboot){
            confirmAction("重启", () -> click.click("重启",3));
            return;
        }
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END
        if(v.getId()==R.id.btn_task_manager){
            click.click("任务管理器",4);
            return;
        }
        if(v.getId()==R.id.btn_clipboard_send){
            click.click("发送剪切板",5);
            return;
        }
        if(v.getId()==R.id.btn_clipboard_open){
            click.click("打开剪切板",6);
            return;
        }
        if(v.getId()==R.id.btn_open_setting){
            click.click("打开设置",7);
            return;
        }
        if(v.getId()==R.id.btn_computer){
            click.click("我的电脑",8);
            return;
        }
        if(v.getId()==R.id.btn_win_center){
            click.click("移动中心",9);
            return;
        }
        if(v.getId()==R.id.btn_win_p){
            click.click("win+p",10);
            return;
        }

        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 显示器1~4 分支已随按钮移出本面板。
        if(v.getId()==R.id.btn_hdr){
            click.click("HDR开关",15);
            return;
        }
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

    }

    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
    private void confirmAction(String action, Runnable onConfirm) {
        new AlertDialog.Builder(getActivity())
                .setTitle("确定执行吗？")
                .setMessage("即将对远端电脑执行：" + action)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(onConfirm!=null){
                            onConfirm.run();
                        }
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }
    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

    public interface onClick{
        void click(String title,int index);
    }

    public void setOnClick(onClick onClick) {
        this.click = onClick;
    }
}
