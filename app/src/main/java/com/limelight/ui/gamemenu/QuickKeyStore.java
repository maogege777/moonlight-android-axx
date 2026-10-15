package com.limelight.ui.gamemenu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.limelight.R;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
/**
 * 「操作」面板「自定义快捷键」分组的数据层。
 *
 * 原先这套数据由主菜单「特色功能 › 快捷键」（GameListQuickFragment）承载，现在整体移入
 * GameFunctionFragment，因此把读取 / 保存 / 删除 / 导入导出集中到这里，供两处共用。
 *
 * 用户自定义项仍沿用旧存储：SharedPreferences quick_axi_keyAssemble，key 前缀
 * quick_assemble_key_，值为 GameMenuQuickBean 的 JSON（Gson 对新增的 icon/sort 字段向后兼容）。
 * 内置项的删除状态是新增能力，单独存 quick_builtin_hidden。
 */
public final class QuickKeyStore {

    public static final String PREF_QUICK_LIST_NAME = "quick_axi_keyAssemble";
    public static final String PREF_QUICK_LIST_KEY = "quick_assemble_key_";
    public static final String PREF_BUILTIN_HIDDEN_NAME = "quick_builtin_hidden";

    public static final String EXPORT_TYPE = "axi_quick_keys";
    public static final int EXPORT_VERSION = 1;

    /** 内置项标识，同时作为 quick_builtin_hidden 的存储 key。 */
    public static final String BUILTIN_PREFIX = "builtin_";

    private QuickKeyStore() {
    }

    // ---------------------------------------------------------------- 内置项

    /**
     * 与旧「快捷键」列表完全一致的内置 Windows 快捷键，新增 icon 映射。
     * datas 使用 KeyboardTranslator 的 VK 码，发送方式与旧列表相同（sendKeys）。
     */
    public static List<GameMenuQuickBean> builtinItems() {
        List<GameMenuQuickBean> items = new ArrayList<>();
        items.add(builtin("esc", "ESC (退出/菜单)", "qk_esc",
                new short[]{KeyboardTranslator.VK_ESCAPE}));
        items.add(builtin("f11", "F11 (网页全屏)", "qk_f11",
                new short[]{KeyboardTranslator.VK_F11}));
        items.add(builtin("alt_f4", "Alt + F4 (关闭应用)", "qk_close",
                new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_F4}));
        items.add(builtin("alt_enter", "Alt + Enter (窗口大小)", "qk_winresize",
                new short[]{KeyboardTranslator.VK_LMENU, KeyboardTranslator.VK_RETURN}));
        items.add(builtin("win", "Win (打开Windows开始菜单)", "qk_win",
                new short[]{KeyboardTranslator.VK_LWIN}));
        items.add(builtin("ctrl_shift_esc", "Ctrl+Shift+ESC (任务管理器)", "qk_taskmgr",
                new short[]{KeyboardTranslator.VK_LCONTROL, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_ESCAPE}));
        items.add(builtin("win_d", "Win + D (返回桌面)", "qk_desktop",
                new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_D}));
        items.add(builtin("win_p", "Win + P (显示器模式)", "qk_proj",
                new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_P}));
        items.add(builtin("win_g", "Win + G (打开Xbox Game Bar)", "qk_gamebar",
                new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_G}));
        items.add(builtin("shift_tab", "Shift + Tab (打开Steam Overlay)", "qk_steam",
                new short[]{KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_TAB}));
        items.add(builtin("win_shift_left", "Win + Shift + left (切换桌面)", "qk_vdesk",
                new short[]{KeyboardTranslator.VK_LWIN, KeyboardTranslator.VK_LSHIFT, KeyboardTranslator.VK_LEFT}));
        return items;
    }

    private static GameMenuQuickBean builtin(String suffix, String name, String icon, short[] datas) {
        GameMenuQuickBean bean = new GameMenuQuickBean(name, datas);
        bean.setId(BUILTIN_PREFIX + suffix);
        bean.setIcon(icon);
        return bean;
    }

    public static boolean isBuiltin(GameMenuQuickBean bean) {
        return bean != null && bean.getId() != null && bean.getId().startsWith(BUILTIN_PREFIX);
    }

    /** 按 id 取内置项定义，导入时用来判断内置项是否被用户编辑过。 */
    private static GameMenuQuickBean findBuiltin(String id) {
        for (GameMenuQuickBean bean : builtinItems()) {
            if (bean.getId().equals(id)) {
                return bean;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- 读取

    /**
     * 「自定义快捷键」分组的完整内容：未被删除的内置项在前（含用户编辑后的覆盖版本），
     * 用户添加项按 sort 顺序追加。
     *
     * @param clearDefaultSpecial 设置里的「禁用默认的特殊按键指令」开关，为 true 时不出现内置项
     */
    public static List<GameMenuQuickBean> load(Context context, boolean clearDefaultSpecial) {
        List<GameMenuQuickBean> items = new ArrayList<>();
        if (!clearDefaultSpecial) {
            SharedPreferences hiddenPref = context.getSharedPreferences(PREF_BUILTIN_HIDDEN_NAME, Activity.MODE_PRIVATE);
            Map<String, String> overrides = (Map<String, String>) filterBuiltinKeys(
                    ((Map<String, String>) context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE).getAll()));
            for (GameMenuQuickBean builtin : builtinItems()) {
                if (hiddenPref.getAll().containsKey(builtin.getId())) {
                    continue;
                }
                String json = overrides.get(builtin.getId());
                GameMenuQuickBean override = json == null ? null : fromJson(json);
                items.add(override == null ? builtin : mergeBuiltinOverride(override, builtin));
            }
        }
        items.addAll(loadCustom(context));
        return items;
    }

    /**
     * 用户编辑内置项后写入的覆盖记录：保留内置项的位置与图标，其余字段取用户的版本。
     * 注意不能把内置项的 datas 补进来——内置项的 datas 是 VK 码、用户项的 codes 是 Android 键码，
     * 补上会让发送层误走 VK 通道，用户重新绑定的按键就不生效了（见 GameFunctionFragment.sendQuickKey）。
     */
    private static GameMenuQuickBean mergeBuiltinOverride(GameMenuQuickBean override, GameMenuQuickBean builtin) {
        if (TextUtils.isEmpty(override.getName())) {
            override.setName(builtin.getName());
        }
        if (TextUtils.isEmpty(override.getIcon())) {
            override.setIcon(builtin.getIcon());
        }
        override.setId(builtin.getId());
        return override;
    }

    private static GameMenuQuickBean fromJson(String json) {
        try {
            return new Gson().fromJson(json, GameMenuQuickBean.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, String> filterBuiltinKeys(Map<String, String> all) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            if (entry.getKey().startsWith(BUILTIN_PREFIX)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** 用户自定义项（不含内置项的覆盖记录）。 */
    public static List<GameMenuQuickBean> loadCustom(Context context) {
        List<GameMenuQuickBean> items = new ArrayList<>();
        SharedPreferences pref = context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE);
        Map<String, String> map = (Map<String, String>) pref.getAll();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().startsWith(BUILTIN_PREFIX)) {
                continue;
            }
            GameMenuQuickBean bean = fromJson(entry.getValue());
            if (bean == null) {
                continue;
            }
            if (TextUtils.isEmpty(bean.getId())) {
                bean.setId(entry.getKey());
            }
            items.add(bean);
        }
        sortCustom(items);
        return items;
    }

    /** 按添加顺序排列：优先 sort，旧数据（sort 全为 0）回落到 id 中的时间戳。 */
    private static void sortCustom(List<GameMenuQuickBean> items) {
        Collections.sort(items, new Comparator<GameMenuQuickBean>() {
            @Override
            public int compare(GameMenuQuickBean a, GameMenuQuickBean b) {
                if (a.getSort() != b.getSort()) {
                    return Integer.compare(a.getSort(), b.getSort());
                }
                String ida = a.getId() == null ? "" : a.getId();
                String idb = b.getId() == null ? "" : b.getId();
                return ida.compareTo(idb);
            }
        });
    }

    private static Set<String> hiddenBuiltins(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_BUILTIN_HIDDEN_NAME, Activity.MODE_PRIVATE);
        return new HashSet<>(pref.getAll().keySet());
    }

    public static int nextSort(Context context) {
        int max = 0;
        for (GameMenuQuickBean bean : loadCustom(context)) {
            max = Math.max(max, bean.getSort());
        }
        return max + 1;
    }

    // ---------------------------------------------------------------- 写入

    public static void saveCustom(Context context, GameMenuQuickBean bean) {
        if (bean == null || TextUtils.isEmpty(bean.getId())) {
            return;
        }
        if (bean.getSort() <= 0) {
            bean.setSort(nextSort(context));
        }
        context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE).edit()
                .putString(bean.getId(), new Gson().toJson(bean))
                .apply();
    }

    public static void removeCustom(Context context, GameMenuQuickBean bean) {
        if (bean == null || TextUtils.isEmpty(bean.getId())) {
            return;
        }
        context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE).edit()
                .remove(bean.getId())
                .apply();
    }

    /** 内置项删除即隐藏，不做逐项恢复（已确认）。 */
    public static void hideBuiltin(Context context, GameMenuQuickBean bean) {
        if (!isBuiltin(bean)) {
            return;
        }
        context.getSharedPreferences(PREF_BUILTIN_HIDDEN_NAME, Activity.MODE_PRIVATE).edit()
                .putBoolean(bean.getId(), true)
                .apply();
    }

    public static void replaceAllCustom(Context context, List<GameMenuQuickBean> beans) {
        SharedPreferences pref = context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        if (beans != null) {
            for (GameMenuQuickBean bean : beans) {
                editor.putString(bean.getId(), new Gson().toJson(bean));
            }
        }
        editor.apply();
    }

    public static void replaceBuiltinHidden(Context context, Set<String> hiddenIds) {
        SharedPreferences pref = context.getSharedPreferences(PREF_BUILTIN_HIDDEN_NAME, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.clear();
        if (hiddenIds != null) {
            for (String id : hiddenIds) {
                editor.putBoolean(id, true);
            }
        }
        editor.apply();
    }

    /**
     * 覆盖内置项的编辑记录（改名 / 换图标 / 重新绑定按键）。
     * 必须单独清理：replaceAllCustom 会清空整个 quick_axi_keyAssemble，内置项的覆盖记录也在其中。
     */
    public static void replaceBuiltinOverrides(Context context, List<GameMenuQuickBean> overrides) {
        SharedPreferences pref = context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        for (String key : pref.getAll().keySet()) {
            if (key.startsWith(BUILTIN_PREFIX)) {
                editor.remove(key);
            }
        }
        if (overrides != null) {
            for (GameMenuQuickBean bean : overrides) {
                if (bean == null || !isBuiltin(bean)) {
                    continue;
                }
                editor.putString(bean.getId(), new Gson().toJson(bean));
            }
        }
        editor.apply();
    }

    // ---------------------------------------------------------------- 图标

    /** 图标 key → drawable 资源；未识别或为空时回落到默认键盘图标。 */
    public static int iconRes(String icon) {
        if (TextUtils.isEmpty(icon)) {
            return R.drawable.ic_axi_qk_default;
        }
        switch (icon) {
            case "qk_esc": return R.drawable.ic_axi_qk_esc;
            case "qk_f11": return R.drawable.ic_axi_qk_f11;
            case "qk_close": return R.drawable.ic_axi_qk_close;
            case "qk_winresize": return R.drawable.ic_axi_qk_winresize;
            case "qk_win": return R.drawable.ic_axi_qk_win;
            case "qk_taskmgr": return R.drawable.ic_axi_qk_taskmgr;
            case "qk_desktop": return R.drawable.ic_axi_qk_desktop;
            case "qk_proj": return R.drawable.ic_axi_qk_proj;
            case "qk_gamebar": return R.drawable.ic_axi_qk_gamebar;
            case "qk_steam": return R.drawable.ic_axi_qk_steam;
            case "qk_vdesk": return R.drawable.ic_axi_qk_vdesk;
            case "qk_bolt": return R.drawable.ic_axi_qk_bolt;
            case "qk_star": return R.drawable.ic_axi_qk_star;
            case "qk_target": return R.drawable.ic_axi_qk_target;
            case "qk_joystick": return R.drawable.ic_axi_qk_joystick;
            case "qk_monitor": return R.drawable.ic_axi_qk_monitor;
            case "qk_window": return R.drawable.ic_axi_qk_window;
            case "qk_clipboard": return R.drawable.ic_axi_qk_clipboard;
            case "qk_default": return R.drawable.ic_axi_qk_default;
            default: return R.drawable.ic_axi_qk_default;
        }
    }

    /** 新增 / 编辑页的可选图标库（本期固定 19 个，后续再扩充）。 */
    public static List<String> iconLibrary() {
        return Arrays.asList(
                "qk_default", "qk_bolt", "qk_star", "qk_target", "qk_joystick",
                "qk_monitor", "qk_window", "qk_clipboard", "qk_esc", "qk_f11",
                "qk_close", "qk_winresize", "qk_win", "qk_taskmgr", "qk_desktop",
                "qk_proj", "qk_gamebar", "qk_steam", "qk_vdesk");
    }

    public static String iconLabel(String key) {
        switch (key) {
            case "qk_default": return "默认";
            case "qk_bolt": return "闪电";
            case "qk_star": return "星形";
            case "qk_target": return "靶心";
            case "qk_joystick": return "摇杆";
            case "qk_monitor": return "显示器";
            case "qk_window": return "窗口";
            case "qk_clipboard": return "剪贴板";
            case "qk_esc": return "ESC";
            case "qk_f11": return "F11";
            case "qk_close": return "关闭";
            case "qk_winresize": return "窗口大小";
            case "qk_win": return "Win";
            case "qk_taskmgr": return "任务管理器";
            case "qk_desktop": return "桌面";
            case "qk_proj": return "投影";
            case "qk_gamebar": return "Game Bar";
            case "qk_steam": return "Steam";
            case "qk_vdesk": return "虚拟桌面";
            default: return key;
        }
    }

    // ---------------------------------------------------------------- 导入导出

    /**
     * 导出全部自定义快捷键（含图标与顺序）以及内置项的删除状态。
     * 用户项的 codes 存的是 Android 键码，内置项的 datas 存的是 VK 码，与各自发送方式一致。
     */
    public static String exportJson(Context context) {
        try {
            JSONObject root = new JSONObject();
            root.put("type", EXPORT_TYPE);
            root.put("version", EXPORT_VERSION);
            root.put("exportedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    .format(new java.util.Date()));

            JSONArray data = new JSONArray();
            for (GameMenuQuickBean bean : loadCustom(context)) {
                data.put(itemJson(bean));
            }
            root.put("data", data);

            JSONArray builtin = new JSONArray();
            Map<String, String> overrides = filterBuiltinKeys(
                    (Map<String, String>) context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE).getAll());
            Set<String> hidden = hiddenBuiltins(context);
            for (GameMenuQuickBean item : builtinItems()) {
                GameMenuQuickBean override = fromJson(overrides.get(item.getId()));
                GameMenuQuickBean effective = override == null ? item : mergeBuiltinOverride(override, item);
                JSONObject object = new JSONObject();
                object.put("id", item.getId());
                object.put("name", effective.getName());
                object.put("icon", effective.getIcon() == null ? "" : effective.getIcon());
                object.put("hidden", hidden.contains(item.getId()));
                if (!TextUtils.isEmpty(effective.getCodes())) {
                    object.put("codes", effective.getCodes());
                }
                if (!TextUtils.isEmpty(effective.getDesc())) {
                    object.put("desc", effective.getDesc());
                }
                JSONArray codes = new JSONArray();
                for (short code : item.getDatas()) {
                    codes.put(code);
                }
                object.put("defaultDatas", codes);
                builtin.put(object);
            }
            root.put("builtin", builtin);

            return root.toString(2);
        } catch (Exception e) {
            return "";
        }
    }

    private static JSONObject itemJson(GameMenuQuickBean bean) throws Exception {
        JSONObject object = new JSONObject();
        object.put("id", bean.getId());
        object.put("name", bean.getName());
        object.put("icon", bean.getIcon() == null ? "" : bean.getIcon());
        object.put("sort", bean.getSort());
        object.put("desc", bean.getDesc() == null ? "" : bean.getDesc());
        if (!TextUtils.isEmpty(bean.getCodes())) {
            object.put("codes", bean.getCodes());
        }
        if (bean.getDatas() != null && bean.getDatas().length > 0) {
            JSONArray codes = new JSONArray();
            for (short code : bean.getDatas()) {
                codes.put(code);
            }
            object.put("datas", codes);
        }
        return object;
    }

    /**
     * 解析导入文件。返回 null 表示格式不正确。
     * 导入语义：覆盖全部自定义快捷键（与「导入虚拟按键配置」一致），内置删除状态一并恢复。
     */
    public static ImportResult parseImport(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            ImportResult result = new ImportResult();

            JSONArray data = root.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject object = data.getJSONObject(i);
                    String name = object.optString("name");
                    if (TextUtils.isEmpty(name)) {
                        continue;
                    }
                    GameMenuQuickBean bean = new GameMenuQuickBean();
                    bean.setName(name);
                    bean.setIcon(object.optString("icon", null));
                    bean.setDesc(object.optString("desc", null));
                    bean.setSort(object.optInt("sort", i + 1));
                    String codes = object.optString("codes", null);
                    if (!TextUtils.isEmpty(codes)) {
                        bean.setCodes(codes);
                    }
                    JSONArray datas = object.optJSONArray("datas");
                    if (datas != null && datas.length() > 0) {
                        short[] values = new short[datas.length()];
                        for (int j = 0; j < datas.length(); j++) {
                            values[j] = (short) datas.getInt(j);
                        }
                        bean.setDatas(values);
                    }
                    if (TextUtils.isEmpty(bean.getCodes())
                            && (bean.getDatas() == null || bean.getDatas().length == 0)) {
                        continue;
                    }
                    String id = object.optString("id", "");
                    if (TextUtils.isEmpty(id) || id.startsWith(BUILTIN_PREFIX)) {
                        id = PREF_QUICK_LIST_KEY + (System.currentTimeMillis() + i);
                    }
                    bean.setId(id);
                    bean.setBtnType(4);
                    result.custom.add(bean);
                }
            }

            JSONArray builtin = root.optJSONArray("builtin");
            if (builtin != null) {
                for (int i = 0; i < builtin.length(); i++) {
                    JSONObject object = builtin.getJSONObject(i);
                    String id = object.optString("id", "");
                    if (!id.startsWith(BUILTIN_PREFIX)) {
                        continue;
                    }
                    if (object.optBoolean("hidden", false)) {
                        result.hiddenBuiltins.add(id);
                    }
                    // 内置项的编辑记录（改名 / 换图标 / 重新绑定按键）一并恢复；
                    // 未编辑过的内置项不写覆盖记录，保持跟随内置定义。
                    String name = object.optString("name", "");
                    String icon = object.optString("icon", "");
                    String codes = object.optString("codes", "");
                    String desc = object.optString("desc", "");
                    GameMenuQuickBean known = findBuiltin(id);
                    if (known == null) {
                        continue;
                    }
                    boolean edited = !TextUtils.isEmpty(codes)
                            || (!TextUtils.isEmpty(name) && !name.equals(known.getName()))
                            || (!TextUtils.isEmpty(icon) && !icon.equals(known.getIcon()));
                    if (!edited) {
                        continue;
                    }
                    GameMenuQuickBean override = new GameMenuQuickBean();
                    override.setId(id);
                    override.setName(TextUtils.isEmpty(name) ? known.getName() : name);
                    override.setIcon(TextUtils.isEmpty(icon) ? known.getIcon() : icon);
                    override.setDesc(desc);
                    override.setBtnType(4);
                    if (!TextUtils.isEmpty(codes)) {
                        override.setCodes(codes);
                    }
                    result.builtinOverrides.add(override);
                }
            }

            sortCustom(result.custom);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public static final class ImportResult {
        public final List<GameMenuQuickBean> custom = new ArrayList<>();
        public final List<GameMenuQuickBean> builtinOverrides = new ArrayList<>();
        public final Set<String> hiddenBuiltins = new HashSet<>();
    }
    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
}
