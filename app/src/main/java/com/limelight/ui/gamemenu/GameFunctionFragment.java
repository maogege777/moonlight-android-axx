package com.limelight.ui.gamemenu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.nvstream.NvConnection;
import com.limelight.ui.AppDialog;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;
import com.limelight.utils.UiHelper;

import java.util.List;

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

    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
    // 「自定义快捷键」分组：原主菜单「特色功能 › 快捷键」整体移入本面板顶部。
    private static final int QUICK_KEY_COLUMNS = 4;
    private LinearLayout quickKeyContainer;
    private boolean enableClearDefaultSpecial;
    private List<GameMenuQuickBean> quickKeys;
    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END

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
        // 显示器1~4 全部位于主菜单第三行，本面板不再放置显示器按钮，也不绑定其点击。
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

        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
        v.findViewById(R.id.btn_quick_add).setOnClickListener(this);
        quickKeyContainer=v.findViewById(R.id.quick_key_container);
        refreshQuickKeys();
        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
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

    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
    public void setEnableClearDefaultSpecial(boolean enableClearDefaultSpecial) {
        this.enableClearDefaultSpecial = enableClearDefaultSpecial;
    }

    /** 按 4 列网格重建「自定义快捷键」分组，顺序为未删除的内置项在前、用户项按添加顺序追加。 */
    private void refreshQuickKeys() {
        if (quickKeyContainer == null || getActivity() == null) {
            return;
        }
        quickKeys = QuickKeyStore.load(getActivity(), enableClearDefaultSpecial);
        quickKeyContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        LinearLayout row = null;
        for (int i = 0; i < quickKeys.size(); i++) {
            if (i % QUICK_KEY_COLUMNS == 0) {
                row = new LinearLayout(getActivity());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, UiHelper.dpToPx(getActivity(), 54));
                rowParams.setMargins(UiHelper.dpToPx(getActivity(), 5), UiHelper.dpToPx(getActivity(), 5),
                        UiHelper.dpToPx(getActivity(), 5), UiHelper.dpToPx(getActivity(), 5));
                quickKeyContainer.addView(row, rowParams);
            }
            final GameMenuQuickBean bean = quickKeys.get(i);
            View item = inflater.inflate(R.layout.item_game_menu_quick_key, row, false);
            ((ImageView) item.findViewById(R.id.iv_quick_icon))
                    .setImageResource(QuickKeyStore.iconRes(bean.getIcon()));
            // 名称留空时不显示名称行（用户确认：不填写就没有名称），
            // 组合键文本（desc）作为副标题显示。
            String label = quickKeyLabel(bean);
            TextView name = item.findViewById(R.id.tx_quick_name);
            if (TextUtils.isEmpty(label)) {
                name.setVisibility(View.GONE);
            } else {
                name.setVisibility(View.VISIBLE);
                name.setText(label);
            }
            TextView desc = item.findViewById(R.id.tx_quick_desc);
            String descText = quickKeyDesc(bean);
            if (TextUtils.isEmpty(descText)) {
                desc.setVisibility(View.GONE);
            } else {
                desc.setVisibility(View.VISIBLE);
                desc.setText(descText);
            }
            item.setOnClickListener(v -> sendQuickKey(bean));
            item.setOnLongClickListener(v -> {
                showQuickKeyMenu(bean);
                return true;
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            params.setMarginEnd(UiHelper.dpToPx(getActivity(), 5));
            row.addView(item, params);
        }
        // 末行不足四列时补空槽，保持与「快捷操作」等原有分组一致的四列对齐
        if (!quickKeys.isEmpty() && quickKeys.size() % QUICK_KEY_COLUMNS != 0) {
            addQuickKeySpacers(row, quickKeys.size());
        }
    }

    private void addQuickKeySpacers(LinearLayout row, int filled) {
        int target = ((filled + QUICK_KEY_COLUMNS - 1) / QUICK_KEY_COLUMNS) * QUICK_KEY_COLUMNS;
        for (int i = filled; i < target; i++) {
            View spacer = new View(getActivity());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            params.setMarginEnd(UiHelper.dpToPx(getActivity(), 5));
            row.addView(spacer, params);
        }
    }

    /**
     * 主标题：取用户输入的名称；名称留空时按钮不显示名称（用户确认：不填写就没有名称）。
     * 旧版带括号说明的名称（"Ctrl+Shift+ESC (任务管理器)"）仍取括号前的组合键作为标题。
     */
    private static String quickKeyLabel(GameMenuQuickBean bean) {
        String name = bean.getName();
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        int bracket = name.indexOf('(');
        if (bracket > 0) {
            name = name.substring(0, bracket);
        }
        return name.trim();
    }

    /** 副标题：旧版名称括号内的用途说明；否则取组合键文本（desc，如 Win+Shift+←）。 */
    private static String quickKeyDesc(GameMenuQuickBean bean) {
        String name = bean.getName();
        if (!TextUtils.isEmpty(name)) {
            int bracket = name.indexOf('(');
            if (bracket > 0 && name.endsWith(")")) {
                return name.substring(bracket + 1, name.length() - 1).trim();
            }
        }
        return bean.getDesc() == null ? "" : bean.getDesc();
    }

    /** 单击：发送组合键并关闭全部菜单（「操作」面板与主菜单一并关闭，已确认）。 */
    private void sendQuickKey(GameMenuQuickBean bean) {
        if (bean == null) {
            return;
        }
        String toastText = quickKeyMenuTitle(bean);
        if (!TextUtils.isEmpty(toastText)) {
            Toast.makeText(getActivity(), toastText, Toast.LENGTH_SHORT).show();
        }
        // 有 codes 的项（默认组项与用户自定义项）走 Android 键码通道，与「设置快捷键」页
        // 虚拟键盘录入的按键同源；只有 datas 的旧版导入项走 VK 码通道，与旧「快捷键」列表一致。
        if (!TextUtils.isEmpty(bean.getCodes())) {
            sendQuickKeyCodes(bean.getCodes());
        } else if (conn != null && bean.getDatas() != null && bean.getDatas().length > 0) {
            GameMenuFragment.sendKeys(conn, bean.getDatas());
        }
        dismissAllDialogs();
    }

    /** 长按：编辑 / 删除（默认组项与用户添加项行为一致）。 */
    private void showQuickKeyMenu(GameMenuQuickBean bean) {
        String[] actions = new String[]{"编辑", "删除"};
        new AlertDialog.Builder(getActivity())
                .setTitle(quickKeyMenuTitle(bean))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        openQuickKeyEditor(bean);
                    } else {
                        confirmDeleteQuickKey(bean);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 名称留空时菜单标题回落组合键文本，都没有时用通用标题。 */
    private static String quickKeyMenuTitle(GameMenuQuickBean bean) {
        String title = quickKeyLabel(bean);
        if (TextUtils.isEmpty(title)) {
            title = quickKeyDesc(bean);
        }
        return TextUtils.isEmpty(title) ? "自定义快捷键" : title;
    }

    private void confirmDeleteQuickKey(GameMenuQuickBean bean) {
        AppDialog.showConfirm(
                getActivity(),
                quickKeyMenuTitle(bean),
                "是否删除此键值？",
                "删除",
                true,
                () -> {
                    QuickKeyStore.removeCustom(getActivity(), bean);
                    refreshQuickKeys();
                },
                null);
    }

    private void openQuickKeyEditor(GameMenuQuickBean existing) {
        GameKeyboardUpdateFragment fragment = new GameKeyboardUpdateFragment();
        fragment.setWidth(fullScreenWidth());
        fragment.setDimAmount(0.8f);
        fragment.setTitle(existing == null ? "设置快捷键" : "编辑快捷键");
        fragment.setKeyFrom(1);
        fragment.setExisting(existing);
        fragment.setOnClick(bean -> {
            if (bean == null) {
                return;
            }
            QuickKeyStore.saveCustom(getActivity(), bean);
            refreshQuickKeys();
        });
        fragment.show(getFragmentManager());
    }

    private int fullScreenWidth() {
        if (getResources().getDisplayMetrics().widthPixels > getResources().getDisplayMetrics().heightPixels) {
            return getResources().getDisplayMetrics().widthPixels;
        }
        return (getResources().getDisplayMetrics().heightPixels * 2) / 3;
    }

    private void sendQuickKeyCodes(String codes) {
        if (TextUtils.isEmpty(codes) || game == null) {
            return;
        }
        String[] keys = codes.split(",");
        for (int i = 0; i < keys.length; i++) {
            game.keyboardEvent(true, Short.parseShort(keys[i]));
        }
        new android.os.Handler().postDelayed(() -> {
            for (int i = keys.length - 1; i >= 0; i--) {
                game.keyboardEvent(false, Short.parseShort(keys[i]));
            }
        }, 25);
    }
    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END

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
        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
        // 标题栏右上角「新增」：打开与旧「快捷键」界面一致的录入面板（含图标选择）。
        if (v.getId() == R.id.btn_quick_add) {
            openQuickKeyEditor(null);
            return;
        }
        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END

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
        // 显示器1~4 分支已随按钮移至主菜单处理。
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
