package com.limelight.ui.gamemenu;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.Game;
import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardController;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.BaseFragmentDialog.BaseGameMenuDialog;
// EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
// GameMenuQuickBean 的引用随「快捷键」入口一并移入 GameFunctionFragment。
// EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
import com.limelight.utils.UiHelper;

import java.security.Key;

import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_GAMEPAD_PREFERENCE;
import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_GAMEPAD_PREFERENCE_VALUE;
import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE;
import static com.limelight.binding.input.virtual_controller.keyboard.KeyBoardControllerConfigurationLoader.OSC_PREFERENCE_VALUE;
/**
 * Description
 * Date: 2024-10-20
 * Time: 16:07
 */
public class GameMenuFragment extends BaseGameMenuDialog implements View.OnClickListener{

    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
    // 麦克风 / 虚拟鼠标 / 手柄键鼠 三个开关按钮已移入「更多」子面板（GameFunctionFragment），
    // 其状态高亮刷新逻辑一并迁移，此处不再保留。
    // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

    @Override
    public int getLayoutRes() {
        return R.layout.dialog_game_menu;
    }

    private Button btn_performance;

    private Button btn_game_pad;

    private Button btn_v_keyboard;

    private Button btn_screen_move;

    // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] BEGIN
    // 虚拟手柄 / 虚拟按键 的「长按切换布局方案」相关状态。
    private SchemeBadgeView badge_game_pad;
    private SchemeBadgeView badge_v_keyboard;
    /** 长按阈值：原生 onLongClick 固定 500ms，按需求提前到 200ms */
    private static final int SCHEME_LONG_PRESS_DELAY_MS = 200;
    private final Handler schemePressHandler = new Handler(Looper.getMainLooper());
    private Runnable schemeLongPressRunnable;
    /** 长按已触发时置位，用于吞掉松手后补发的 onClick（避免又切换一次显隐） */
    private boolean schemeLongPressFired;
    private PopupWindow schemePopup;
    // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] END

    private TextView tx_title_battery;

    private Game game;
    private NvConnection conn;

    @Override
    public void bindView(View v) {
        super.bindView(v);
        btn_performance=v.findViewById(R.id.btn_performance);
        btn_game_pad=v.findViewById(R.id.btn_game_pad);
        btn_v_keyboard=v.findViewById(R.id.btn_v_keyboard);
        btn_screen_move=v.findViewById(R.id.btn_screen_move);
        // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] BEGIN
        badge_game_pad=v.findViewById(R.id.badge_game_pad);
        badge_v_keyboard=v.findViewById(R.id.badge_v_keyboard);
        // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] END
        if(game!=null){
            btn_performance.setBackgroundResource(game.prefConfig.enablePerfOverlay?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            btn_game_pad.setBackgroundResource(game.prefConfig.onscreenController?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            btn_v_keyboard.setBackgroundResource(game.prefConfig.enableKeyboard?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            btn_screen_move.setBackgroundResource(game.getScreenMoveZoom()?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
        }
        // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] BEGIN
        attachSchemeLongPress(btn_game_pad, true);
        attachSchemeLongPress(btn_v_keyboard, false);
        refreshSchemeBadges();
        // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] END
        v.findViewById(R.id.btn_unlink).setOnClickListener(this);
        v.findViewById(R.id.btn_exit).setOnClickListener(this);
        v.findViewById(R.id.btn_swicth_screen).setOnClickListener(this);
        v.findViewById(R.id.btn_performance).setOnClickListener(this);
        v.findViewById(R.id.btn_game_pad).setOnClickListener(this);
        v.findViewById(R.id.btn_v_keyboard).setOnClickListener(this);
        v.findViewById(R.id.btn_keyboard).setOnClickListener(this);
        v.findViewById(R.id.btn_screen_move).setOnClickListener(this);
        v.findViewById(R.id.bt_touch_sensitivity).setOnClickListener(this);
        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
        // 「快捷键」入口已移除（bt_quick_list），功能融入「操作」面板的「自定义快捷键」分组。
        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
        v.findViewById(R.id.bt_touch_list).setOnClickListener(this);
        v.findViewById(R.id.btn_soft_keyboard).setOnClickListener(this);
        v.findViewById(R.id.btn_desktop).setOnClickListener(this);
        v.findViewById(R.id.btn_window).setOnClickListener(this);
        v.findViewById(R.id.bt_touch_sensitivity).setOnClickListener(this);
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 显示器1~4 上移到主菜单直接发送 Ctrl+Alt+Shift+F1~F4；
        // 原「更多」入口与侧边栏「操作」打开的是同一面板（GameFunctionFragment），已移除「更多」，
        // 显示器4 恢复显示并占据原「更多」的位置。
        v.findViewById(R.id.btn_display_1).setOnClickListener(this);
        v.findViewById(R.id.btn_display_2).setOnClickListener(this);
        v.findViewById(R.id.btn_display_3).setOnClickListener(this);
        v.findViewById(R.id.btn_display_4).setOnClickListener(this);
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END
        v.findViewById(R.id.bt_virtual_view).setOnClickListener(this);
        v.findViewById(R.id.bt_display).setOnClickListener(this);
        v.findViewById(R.id.bt_device).setOnClickListener(this);
        v.findViewById(R.id.bt_audio_rumble).setOnClickListener(this);
        v.findViewById(R.id.bt_other_setting).setOnClickListener(this);
        v.findViewById(R.id.btn_soft_function).setOnClickListener(this);

        v.findViewById(R.id.btn_performance).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if(game!=null){
                    game.switchHUD();
                }
                return false;
            }
        });
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // refreshMicButton() 已随麦克风按钮迁移至 GameFunctionFragment。
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END
//        v.findViewById(R.id.btn_soft_keyboard).setOnLongClickListener(new View.OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                if(game!=null){
//                    game.sendClipboardText();
//                }
//                return false;
//            }
//        });

        tx_title_battery=v.findViewById(R.id.tx_title_battery);
        tx_title_battery.setText(getPhoneBattery(getActivity())+"%");
    }

    @Override
    public float getDimAmount() {
        return super.getDimAmount();
    }

    @Override
    public void onClick(View v) {
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 虚拟鼠标 / 麦克风 / 手柄键鼠 的处理逻辑已移入 GameFunctionFragment（「更多」子面板）。
        // 显示器1~3 在主菜单直接发送 Ctrl+Alt+Shift+F1~F3；「更多」与原「操作」入口一致。
        if (v.getId() == R.id.btn_display_1) {
            if (conn != null) {
                sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F1});
            }
            return;
        }
        if (v.getId() == R.id.btn_display_2) {
            if (conn != null) {
                sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F2});
            }
            return;
        }
        if (v.getId() == R.id.btn_display_3) {
            if (conn != null) {
                sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F3});
            }
            return;
        }
        if (v.getId() == R.id.btn_display_4) {
            if (conn != null) {
                sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_F4});
            }
            return;
        }

        //操作（侧边栏入口；原主菜单「更多」与其重复，已移除）
        if(v.getId()==R.id.btn_soft_function){
            GameFunctionFragment fragment=new GameFunctionFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("操作");
            fragment.setGame(game);
            fragment.setConn(conn);
            // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
            // 「自定义快捷键」分组沿用设置里的「禁用默认的特殊按键指令」开关控制内置项。
            if(game!=null){
                fragment.setEnableClearDefaultSpecial(game.prefConfig.enableClearDefaultSpecial);
            }
            // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
            fragment.setOnClick(new GameFunctionFragment.onClick() {
                @Override
                public void click(String title, int index) {
                    if(conn==null){
                        return;
                    }
                    switch (index){
                        case 0://注销
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_I})), 200);
                            break;
                        case 1://关机
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_U})), 200);
                            break;
                        case 2://睡眠
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_S})), 200);
                            break;
                        case 3://重启
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            new Handler().postDelayed((() -> sendKeys(new short[]{KeyboardTranslator.VK_U, KeyboardTranslator.VK_R})), 200);
                            break;
                        case 4://任务管理器
                            sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE});
                            break;
                        case 5://发送剪切板
                            if(game!=null){
                                game.sendClipboardText();
                            }
                            break;
                        case 6://打开剪切板
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_V});
                            break;
                        case 7://系统设置
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_I});
                            break;
                        case 8://我的电脑
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_E});
                            break;
                        case 9://移动中心
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_X});
                            break;
                        case 10://Win+P
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_P});
                            break;
                        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
                        // 显示器1~4 已移出「更多」子面板（显示器1~3 上移至主菜单处理，显示器4 隐藏），此处不再回调。
                        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END
                        case 15://HDR开关
                            sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_B});
                            break;
                    }
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        //断开链接
        if(v.getId()==R.id.btn_unlink){
            dismiss();
            if(game!=null){
                game.finish();
            }
            return;
        }
        if(v.getId()==R.id.btn_exit){
            dismiss();
            if(game!=null){
                game.isQuitSteamingFlag=true;
                game.disconnect();
            }
            return;
        }

        if(v.getId()==R.id.btn_swicth_screen){
            dismiss();
            if(game!=null){
                game.switchLandscapePortraitScreen();
            }
            return;
        }
        if(v.getId()==R.id.btn_game_pad){
            // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [MODIFIED] BEGIN
            // 长按已弹出方案浮层时，松手补发的这次 click 直接丢弃，不再切换显隐
            if(schemeLongPressFired){
                schemeLongPressFired=false;
                return;
            }
            // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [MODIFIED] END
            if(game!=null){
                game.showHideVirtualController();
                btn_game_pad.setBackgroundResource(game.prefConfig.onscreenController?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
                // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW]
                refreshSchemeBadges();
            }
            return;
        }

        if(v.getId()==R.id.btn_performance){
            if(game!=null){
                game.showHUD();
                btn_performance.setBackgroundResource(game.prefConfig.enablePerfOverlay?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
            }
            return;
        }

        if(v.getId()==R.id.btn_v_keyboard){
            // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [MODIFIED] BEGIN
            if(schemeLongPressFired){
                schemeLongPressFired=false;
                return;
            }
            // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [MODIFIED] END
            if(game!=null){
                game.showHideKeyboardController();
                btn_v_keyboard.setBackgroundResource(game.prefConfig.enableKeyboard?R.drawable.ic_game_menu_btn_green_selector:R.drawable.ic_game_menu_btn_selector);
                // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW]
                refreshSchemeBadges();
            }
            return;
        }

        if(v.getId()==R.id.btn_keyboard){
            if(game!=null){
                game.showHidekeyBoardLayoutController();
            }
            return;
        }

        if(v.getId()==R.id.btn_soft_keyboard){
            dismiss();
            if(game!=null){
                toggleKeyboard();
            }
            return;
        }

        if(v.getId()==R.id.btn_screen_move){
            dismiss();
            if(game!=null){
                game.screenMoveZoom();
            }
            return;
        }

        if(v.getId()==R.id.btn_desktop){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D});
            }
            return;
        }

        if(v.getId()==R.id.btn_window){
            if(conn!=null){
                sendKeys(new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_TAB});
            }
            return;
        }

        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] BEGIN
        // 麦克风 / 手柄键鼠 的点击处理已移入 GameFunctionFragment（「更多」子面板）。
        // EXTENSION DEVELOPMENT [EXT-GAME-MENU-RESTRUCTURE] [MODIFIED] END

        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
        // 原「快捷键」入口（bt_quick_list）已移除：内置项与用户自定义项整体移入
        // GameFunctionFragment 的「自定义快捷键」分组，「禁用默认的特殊按键指令」开关继续生效。
        // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
        if(v.getId()==R.id.bt_touch_list){
            // EXTENSION DEVELOPMENT [EXT-TOUCHPAD-MULTI-GESTURE] [MODIFIED] BEGIN
            // Keep actions appended after the mouse modes stable when extensions add modes.
            final int mouseModeCount = getResources()
                    .getStringArray(R.array.mouse_model_names_axi).length;
            // EXTENSION DEVELOPMENT [EXT-TOUCHPAD-MULTI-GESTURE] [MODIFIED] END
            GameListMouseFragment fragment=new GameListMouseFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("鼠标与触控");
            fragment.setOnClick(new GameListMouseFragment.onClick() {
                @Override
                public void click(String title, int index) {
                    // EXTENSION DEVELOPMENT [EXT-TOUCHPAD-MULTI-GESTURE] [MODIFIED] BEGIN
                    if(!TextUtils.isEmpty(title)&&index!=mouseModeCount + 1){
                        Toast.makeText(getActivity(),title,Toast.LENGTH_SHORT).show();
                    }
                    if(game==null||index<0){
                        return;
                    }
                    if(index==mouseModeCount){
                        game.switchMouseLocalCursor();
                        return;
                    }
                    if(index==mouseModeCount + 1){
                        game.prefConfig.absoluteMouseMode=!game.prefConfig.absoluteMouseMode;
                        Toast.makeText(getActivity(),"远程桌面鼠标模式"+(game.prefConfig.absoluteMouseMode?"已启用！":"已禁用！"),Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if(index==mouseModeCount + 2){
                        if(conn!=null){
                            sendKeys(new short[]{KeyboardTranslator.VK_LCONTROL,KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_N});
                        }
                        return;
                    }
                    // EXTENSION DEVELOPMENT [EXT-TOUCHPAD-MULTI-GESTURE] [MODIFIED] END
                    game.switchMouseModel(index);
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_touch_sensitivity){
            GameTouchFragment fragment=new GameTouchFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("触控灵敏度");
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_display){
            GameDisplayFragment fragment=new GameDisplayFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("显示");
            fragment.setOnClick(new GameDisplayFragment.onClick() {
                @Override
                public void click() {
                    dismiss();
                    if(game!=null){
                        game.finish();
                    }
                }
            });
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_device){
            GameDisplayDeviceFragment fragment=new GameDisplayDeviceFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("外设");
            fragment.setOnClick((index, flag) -> {
                if(index==1){
                    game.setDualSenseTrigger();
                }
            });
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId() == R.id.bt_audio_rumble){
            GameAudioHapticsFragment fragment = new GameAudioHapticsFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("音频震动");
            fragment.setOnSettingsChangedListener(() -> {
                if (game != null) {
                    game.setAudioHapticsSettings();
                }
            });
            fragment.setPrefConfig(game == null ? new PreferenceConfiguration() : game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId() == R.id.bt_other_setting){
            GameDisplaySettingFragment fragment=new GameDisplaySettingFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("杂项");
            fragment.setOnClick(new GameDisplaySettingFragment.onClick() {
                @Override
                public void click(int index,boolean flag) {
                    if(game==null){
                        return;
                    }
                    //悬浮球
                    if(index==0){
                        if(flag){
                            game.showFloatView();
                            return;
                        }
                        game.hideFloatView();
                        return;
                    }
                    //显示震动信息
                    if(index==1){
                        game.switchPerformanceRumbleHUD();
                        return;
                    }
                    //性能信息点击
                    if(index==2){
                        game.switchPerformanceLiteHudclick();
                        return;
                    }
                    //性能信息缩放
                    if(index==3){
                        game.setPerformanceOverlayZoom();
                        return;
                    }
                    //模拟体感
                    if(index==4){
                        game.setMotionForceGyro();
                        return;
                    }
                    //性能信息 边距
                    if(index==5){
                        game.setPerformanceOverlayLiteMagin();
                        return;
                    }
                    //完整/精简性能信息
                    if(index==6){
                        game.setPerformanceOverlayMode();
                        return;
                    }
                }
            });
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.show(getFragmentManager());
            return;
        }

        if(v.getId()==R.id.bt_virtual_view){
            GameMenuVirtualViewFragment fragment=new GameMenuVirtualViewFragment();
            fragment.setWidth(UiHelper.dpToPx(getActivity(),364));
            fragment.setTitle("虚拟手柄与虚拟按键");
            fragment.setGamePadMode(game==null? KeyBoardController.ControllerMode.NONE:game.getVirtualControllerMode());
            fragment.setGameKeyMode(game==null? KeyBoardController.ControllerMode.NONE:game.getVirtualKeyControllerMode());
            fragment.setPrefConfig(game==null?new PreferenceConfiguration():game.prefConfig);
            fragment.setDsTouchpadState(game != null && game.isDsTouchpadSupported(),
                    game != null && game.isDsTouchpadVisible());
            fragment.setOnClick(new GameMenuVirtualViewFragment.onClick() {
                @Override
                public void click(String name, int index) {
                    if(game==null){
                        return;
                    }
                    game.updateVirtualView();
//                    Toast.makeText(getActivity(),"更新成功！",Toast.LENGTH_SHORT).show();
                }

                @Override
                public void switchModeGamePad(String name, KeyBoardController.ControllerMode mode) {
                    if(game==null){
                        return;
                    }
                    game.switchVirtualController(mode);
                }

                @Override
                public void switchModeGameKey(String name, KeyBoardController.ControllerMode mode) {
                    if(game==null){
                        return;
                    }
                    game.switchVirtualKeyController(mode);
                }

                @Override
                public boolean toggleDsTouchpad() {
                    return game != null && game.toggleDsTouchpad();
                }
            });
            fragment.show(getFragmentManager());
            return;
        }

    }

    private void toggleKeyboard(){
        if (!game.hasWindowFocus()) {
            new Handler().postDelayed(() -> toggleKeyboard(),10);
            return;
        }
        game.toggleKeyboard();
    }

    public void setGame(Game game) {
        this.game = game;
    }

    public void setConn(NvConnection conn) {
        this.conn = conn;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (game != null) {
            game.onGameMenuDismissed();
            game.setVirtualMouseInputSuppressed(false);
            game.setVideoZoomInputSuppressed(false);
        }
    }


    private static byte getModifier(short key) {
        switch (key) {
            case KeyboardTranslator.VK_LSHIFT:
                return KeyboardPacket.MODIFIER_SHIFT;
            case KeyboardTranslator.VK_LCONTROL:
                return KeyboardPacket.MODIFIER_CTRL;
            case KeyboardTranslator.VK_LWIN:
                return KeyboardPacket.MODIFIER_META;
            case KeyboardTranslator.VK_LMENU:
                return KeyboardPacket.MODIFIER_ALT;
            default:
                return 0;
        }
    }

    public void sendKeys(short[] keys) {
        sendKeys(conn,keys);
    }

    public static void sendKeys(NvConnection conn, short[] keys) {
        final byte[] modifier = {(byte) 0};

        for (short key : keys) {
            conn.sendKeyboardInput(key, KeyboardPacket.KEY_DOWN, modifier[0], (byte) 0);

            // Apply the modifier of the pressed key, e.g. CTRL first issues a CTRL event (without
            // modifier) and then sends the following keys with the CTRL modifier applied
            modifier[0] |= getModifier(key);
        }

        new Handler().postDelayed((() -> {

            for (int pos = keys.length - 1; pos >= 0; pos--) {
                short key = keys[pos];

                // Remove the keys modifier before releasing the key
                modifier[0] &= ~getModifier(key);

                conn.sendKeyboardInput(key, KeyboardPacket.KEY_UP, modifier[0], (byte) 0);
            }
        }), KEY_UP_DELAY);
    }

    private static final long KEY_UP_DELAY = 25;


    private int getPhoneBattery(Context context) {
        try{
            int level = 0;
            Intent batteryInfoIntent = context.getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            level = batteryInfoIntent.getIntExtra("level", 0);
            int batterySum = batteryInfoIntent.getIntExtra("scale", 100);
            return 100 * level / batterySum;
        }catch (Exception e){
            e.printStackTrace();
        }
        return 100;
    }

    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
    // sendQuickKeylist() 已随「快捷键」入口一并移入 GameFunctionFragment（自定义快捷键发送逻辑）。
    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END

    // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] BEGIN
    // ==================== 长按切换虚拟手柄 / 虚拟按键布局方案 ====================
    // 交互约定：
    //   短按  → 保持原有「显示/隐藏」行为，按钮绿色高亮同步（行为零改动）
    //   长按  → 200ms 后在按钮正下方（空间不足则正上方）弹出方案列表，就近点选
    //   选中  → 写 pref（gamepad_axi_list / keyboard_axi_list）→ 刷新角标 → 热重载布局
    // 与二级面板「虚拟手柄与虚拟按键」里的 RadioGroup 共用同一份 pref，两处始终一致。

    /**
     * 给按钮挂上 200ms 长按判定。
     * 用 OnTouchListener + Handler 而不是 setOnLongClickListener，是因为原生长按固定 500ms，
     * 且这里需要在长按后吞掉补发的 click；返回 false 保证短按仍走原有 OnClickListener。
     */
    private void attachSchemeLongPress(final Button btn, final boolean isGamePad) {
        if (btn == null) {
            return;
        }
        btn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelSchemeLongPress();
                        schemeLongPressFired = false;
                        scheduleSchemeLongPress(v, isGamePad);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // 手指滑出按钮范围视为放弃长按，避免误弹
                        if (event.getX() < 0 || event.getY() < 0
                                || event.getX() > v.getWidth() || event.getY() > v.getHeight()) {
                            cancelSchemeLongPress();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelSchemeLongPress();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private void scheduleSchemeLongPress(final View anchor, final boolean isGamePad) {
        schemeLongPressRunnable = new Runnable() {
            @Override
            public void run() {
                schemeLongPressFired = true;
                if (isAdded() && anchor != null) {
                    showSchemePopup(anchor, isGamePad);
                }
            }
        };
        schemePressHandler.postDelayed(schemeLongPressRunnable, SCHEME_LONG_PRESS_DELAY_MS);
    }

    private void cancelSchemeLongPress() {
        if (schemeLongPressRunnable != null) {
            schemePressHandler.removeCallbacks(schemeLongPressRunnable);
            schemeLongPressRunnable = null;
        }
    }

    private void dismissSchemePopup() {
        if (schemePopup != null) {
            try {
                schemePopup.dismiss();
            } catch (Exception ignored) {
                // 窗口已脱离，忽略
            }
            schemePopup = null;
        }
    }

    /**
     * 在 anchor 就近弹出方案列表：优先按钮正下方，底部空间不足时翻到正上方，
     * 水平方向以按钮中心对齐并夹在屏幕安全边距内。
     */
    private void showSchemePopup(final View anchor, final boolean isGamePad) {
        final Context ctx = getActivity();
        if (ctx == null || anchor == null) {
            return;
        }
        dismissSchemePopup();

        final String[] names = getResources().getStringArray(
                isGamePad ? R.array.gamepad_axi_names : R.array.keyboard_axi_names);
        final String[] values = getResources().getStringArray(
                isGamePad ? R.array.gamepad_axi_values : R.array.keyboard_axi_values);
        final String prefKey = isGamePad ? OSC_GAMEPAD_PREFERENCE : OSC_PREFERENCE;
        final String defValue = isGamePad ? OSC_GAMEPAD_PREFERENCE_VALUE : OSC_PREFERENCE_VALUE;
        final String current = PreferenceManager.getDefaultSharedPreferences(ctx)
                .getString(prefKey, defValue);
        final int currentIndex = indexOfValue(values, current);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_osc_scheme_popup);
        root.setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6));

        TextView header = new TextView(ctx);
        header.setText(isGamePad ? "虚拟手柄布局方案" : "虚拟按键布局方案");
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        header.setTextColor(0xFF9C9CA0);
        header.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(6));
        root.addView(header);

        for (int i = 0; i < names.length; i++) {
            final int index = i;
            root.addView(createSchemeRow(ctx, i + 1, names[i], i == currentIndex,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            applyScheme(isGamePad, values, prefKey, index);
                        }
                    }));
        }

        final int popupWidth = dpToPx(168);
        final PopupWindow popup = new PopupWindow(root, popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        // 透明背景是「点外部自动关闭」在低版本上生效的前提
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(dpToPx(8));
        }

        // 先量出真实高度，才能判断向上还是向下弹
        root.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final int popupHeight = root.getMeasuredHeight();

        // showAsDropDown 的偏移是相对 anchor 在屏幕上的位置计算的，
        // 因此这里统一用「屏幕坐标 + 屏幕尺寸」，不能混用 Dialog 窗口（右侧 364dp 面板）的宽高。
        final int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);

        final int containerW = getResources().getDisplayMetrics().widthPixels;
        final int containerH = getResources().getDisplayMetrics().heightPixels;

        final int margin = dpToPx(8);
        int xoff = (anchor.getWidth() - popupWidth) / 2;
        final int left = loc[0] + xoff;
        if (left < margin) {
            xoff += margin - left;
        } else if (left + popupWidth > containerW - margin) {
            xoff -= (left + popupWidth) - (containerW - margin);
        }

        final int gap = dpToPx(6);
        final int spaceBelow = containerH - (loc[1] + anchor.getHeight());
        final int yoff;
        if (spaceBelow >= popupHeight + gap + margin) {
            yoff = gap;                                              // 正下方
        } else {
            yoff = -(anchor.getHeight() + popupHeight + gap);        // 翻到正上方
        }

        schemePopup = popup;
        popup.showAsDropDown(anchor, xoff, yoff);
    }

    /** 单行方案：左侧数字小方块 + 名称，当前方案额外显示对勾并高亮。 */
    private View createSchemeRow(Context ctx, int number, String label, boolean selected,
                                 View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(6), dpToPx(9), dpToPx(6), dpToPx(9));
        row.setOnClickListener(listener);

        if (selected) {
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dpToPx(6));
            rowBg.setColor(0xFF232B1A);
            row.setBackground(rowBg);
        }

        TextView chip = new TextView(ctx);
        chip.setText(String.valueOf(number));
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(0xFFFFFFFF);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(dpToPx(3));
        chipBg.setColor(selected ? 0xFF79B033 : 0xFF3C3C40);
        chip.setBackground(chipBg);
        LinearLayout.LayoutParams chipLp =
                new LinearLayout.LayoutParams(dpToPx(16), dpToPx(16));
        chipLp.rightMargin = dpToPx(8);
        row.addView(chip, chipLp);

        TextView text = new TextView(ctx);
        text.setText(label);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        text.setTextColor(selected ? 0xFF96D341 : 0xFFE6E6E6);
        if (selected) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        row.addView(text, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (selected) {
            TextView check = new TextView(ctx);
            check.setText("✓");
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            check.setTextColor(0xFF96D341);
            row.addView(check);
        }
        return row;
    }

    /** 写入方案 pref 并热重载；与「虚拟手柄与虚拟按键」面板共用同一份 pref，不产生两套状态。 */
    private void applyScheme(boolean isGamePad, String[] values, String prefKey, int index) {
        final Context ctx = getActivity();
        if (ctx == null || index < 0 || index >= values.length) {
            return;
        }

        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(prefKey, values[index])
                .commit();

        refreshSchemeBadges();

        // 复用既有刷新链路：重读 pref → 重新加载 axi_<方案>.txt → 立即生效。
        // 手柄/按键当前为隐藏状态时这里不会做多余的事，下次显示自然用新方案。
        if (game != null) {
            game.updateVirtualView();
        }

        Toast.makeText(ctx, (isGamePad ? "虚拟手柄" : "虚拟按键")
                + "已切换到方案" + (index + 1), Toast.LENGTH_SHORT).show();
        dismissSchemePopup();
    }

    /** 让两个角标始终显示当前生效的方案序号，并跟随手柄/按键的启用状态换底色。 */
    private void refreshSchemeBadges() {
        final Context ctx = getActivity();
        if (ctx == null || badge_game_pad == null || badge_v_keyboard == null) {
            return;
        }

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        final String[] gamePadValues = getResources().getStringArray(R.array.gamepad_axi_values);
        final String[] keyboardValues = getResources().getStringArray(R.array.keyboard_axi_values);

        final int gamePadIndex = indexOfValue(gamePadValues,
                prefs.getString(OSC_GAMEPAD_PREFERENCE, OSC_GAMEPAD_PREFERENCE_VALUE));
        final int keyboardIndex = indexOfValue(keyboardValues,
                prefs.getString(OSC_PREFERENCE, OSC_PREFERENCE_VALUE));

        badge_game_pad.setSchemeNumber(gamePadIndex + 1);
        badge_v_keyboard.setSchemeNumber(keyboardIndex + 1);

        badge_game_pad.setActive(game == null || game.prefConfig.onscreenController);
        badge_v_keyboard.setActive(game == null || game.prefConfig.enableKeyboard);
    }

    private static int indexOfValue(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private int dpToPx(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        // 菜单关闭时一并收掉浮层与未触发的长按计时，避免窗口泄漏
        cancelSchemeLongPress();
        dismissSchemePopup();
        super.onDestroyView();
    }
    // EXTENSION DEVELOPMENT [EXT-OSC-SCHEME-LONGPRESS] [NEW] END
}
