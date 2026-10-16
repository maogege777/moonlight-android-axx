package com.limelight.ui.gamemenu;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.limelight.R;
import com.limelight.ui.gamemenu.bean.GameMenuQuickBean;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] BEGIN
/**
 * 「操作」面板「自定义快捷键」分组的数据层。
 *
 * v4 变更（用户确认）：默认快捷键不再以「内置项」形式在运行时注入，而是首次运行时
 * 初始化（seed）为一组普通的自定义数据写入存储。之后它们与用户自己添加的项完全等价：
 * 可编辑、可删除，并被导出 / 导入全量覆盖——不再有「导入后还要手动删多余的内置项」的问题。
 *
 * 存储：SharedPreferences quick_axi_keyAssemble，key 前缀 quick_assemble_key_，
 * 值为 GameMenuQuickBean 的 JSON（Gson 对 icon/sort 字段向后兼容）。
 * 默认组使用固定 id 前缀 quick_assemble_key_seed_，用于「禁用默认的特殊按键指令」开关
 * 的隐藏过滤与首启初始化判断。meta 文件 quick_axi_keyAssemble_meta 记录 seeded 标记，
 * 保证初始化只发生一次（导入成功也视为已初始化，不会再次追加默认组）。
 *
 * 兼容：旧版（v3）的内置项覆盖记录（builtin_ 前缀）与隐藏状态（quick_builtin_hidden）
 * 不再注入界面，loadCustom 跳过 builtin_ 记录；v1 导出文件的 builtin 段被忽略，
 * data 段照常导入。
 */
public final class QuickKeyStore {

    public static final String PREF_QUICK_LIST_NAME = "quick_axi_keyAssemble";
    public static final String PREF_QUICK_LIST_KEY = "quick_assemble_key_";
    public static final String PREF_META_NAME = "quick_axi_keyAssemble_meta";
    public static final String PREF_BUILTIN_HIDDEN_NAME = "quick_builtin_hidden"; // v3 遗留，仅保留不再读写

    /** 默认组项的固定 id 前缀。带该前缀的项在「禁用默认的特殊按键指令」开启时隐藏。 */
    public static final String SEED_ID_PREFIX = PREF_QUICK_LIST_KEY + "seed_";

    private static final String SEED_KEY = "seeded";

    public static final String EXPORT_TYPE = "axi_quick_keys";
    public static final int EXPORT_VERSION = 2;

    /** v3 遗留前缀：旧内置项覆盖记录，导入时会被换成普通 id，读取时跳过。 */
    public static final String BUILTIN_PREFIX = "builtin_";

    private QuickKeyStore() {
    }

    // ---------------------------------------------------------------- 默认组（seed）

    /**
     * 默认快捷键组：与旧内置列表等价的常用 Windows 组合键，但以普通自定义数据的形式存在。
     * codes 使用「设置快捷键」页虚拟键盘的 Android 键码（与用户自行录入的组合键同源），
     * 发送时经 KeyboardTranslator 翻译为 VK 码，与旧内置项的发送结果一致。
     * 名称一律留空（用户确认：名称可留空，按钮只显示图标 + 组合键），desc 为组合键文本。
     */
    public static List<GameMenuQuickBean> defaultItems() {
        List<GameMenuQuickBean> items = new ArrayList<>();
        items.add(seed("esc", "111", "ESC", "qk_esc"));
        items.add(seed("alt_tab", "57,61", "Alt+Tab", "qk_window"));
        items.add(seed("win", "117", "Win", "qk_win"));
        items.add(seed("win_e", "117,33", "Win+E", "qk_monitor"));
        items.add(seed("win_i", "117,37", "Win+I", "qk_default"));
        items.add(seed("alt_f4", "57,134", "Alt+F4", "qk_close"));
        items.add(seed("win_d", "117,32", "Win+D", "qk_desktop"));
        items.add(seed("win_shift_left", "117,59,21", "Win+Shift+←", "qk_vdesk"));
        items.add(seed("ctrl_shift_esc", "113,59,111", "Ctrl+Shift+Esc", "qk_taskmgr"));
        items.add(seed("win_g", "117,35", "Win+G", "qk_gamebar"));
        return items;
    }

    private static GameMenuQuickBean seed(String suffix, String codes, String desc, String icon) {
        GameMenuQuickBean bean = new GameMenuQuickBean();
        bean.setId(SEED_ID_PREFIX + suffix);
        bean.setName("");
        bean.setCodes(codes);
        bean.setDesc(desc);
        bean.setIcon(icon);
        bean.setBtnType(4);
        return bean;
    }

    /**
     * 首次运行时初始化默认组：只发生一次（meta.seeded 标记）。
     * - 已有自定义项（升级自旧版本且用户已自行添加过）时不注入，只标记，避免污染用户数据；
     * - 「禁用默认的特殊按键指令」开启时不注入（与旧版不出现内置项的语义一致）。
     */
    public static void ensureSeeded(Context context, boolean clearDefaultSpecial) {
        SharedPreferences meta = context.getSharedPreferences(PREF_META_NAME, Activity.MODE_PRIVATE);
        if (meta.getBoolean(SEED_KEY, false)) {
            return;
        }
        meta.edit().putBoolean(SEED_KEY, true).apply();

        SharedPreferences pref = context.getSharedPreferences(PREF_QUICK_LIST_NAME, Activity.MODE_PRIVATE);
        for (String key : pref.getAll().keySet()) {
            if (!key.startsWith(BUILTIN_PREFIX)) {
                return; // 已有用户自定义项：不追加默认组
            }
        }
        if (clearDefaultSpecial) {
            return;
        }
        SharedPreferences.Editor editor = pref.edit();
        int sort = 1;
        for (GameMenuQuickBean bean : defaultItems()) {
            bean.setSort(sort++);
            editor.putString(bean.getId(), new Gson().toJson(bean));
        }
        editor.apply();
    }

    /** 导入成功即视为已初始化，防止导入后再次追加默认组。 */
    public static void markSeeded(Context context) {
        context.getSharedPreferences(PREF_META_NAME, Activity.MODE_PRIVATE).edit()
                .putBoolean(SEED_KEY, true)
                .apply();
    }

    public static boolean isSeedItem(GameMenuQuickBean bean) {
        return bean != null && bean.getId() != null && bean.getId().startsWith(SEED_ID_PREFIX);
    }

    // ---------------------------------------------------------------- 读取

    /**
     * 「自定义快捷键」分组的全部数据（默认组 + 用户添加项），按 sort 顺序。
     *
     * @param clearDefaultSpecial 设置里的「禁用默认的特殊按键指令」开关：为 true 时隐藏默认组项
     *                            （数据仍在存储中，可被导出 / 导入）
     */
    public static List<GameMenuQuickBean> load(Context context, boolean clearDefaultSpecial) {
        ensureSeeded(context, clearDefaultSpecial);
        List<GameMenuQuickBean> items = loadCustom(context);
        if (clearDefaultSpecial) {
            for (Iterator<GameMenuQuickBean> it = items.iterator(); it.hasNext(); ) {
                if (isSeedItem(it.next())) {
                    it.remove();
                }
            }
        }
        return items;
    }

    /** 全部自定义项（含默认组项；跳过 v3 遗留的 builtin_ 覆盖记录）。 */
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

    private static GameMenuQuickBean fromJson(String json) {
        try {
            return new Gson().fromJson(json, GameMenuQuickBean.class);
        } catch (Exception ignored) {
            return null;
        }
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

    /** 导入语义：覆盖全部自定义快捷键（含默认组项）。导入后不再追加默认组。 */
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
        markSeeded(context);
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
     * 导出全部自定义快捷键（含默认组项、图标与顺序）。v2 格式只有 data 段：
     * 导出什么就导入什么，导入覆盖全部，不再有独立的内置项状态。
     * 用户项的 codes 是 Android 键码；旧版导入项可能带 datas（VK 码），原样保留。
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

            return root.toString(2);
        } catch (Exception e) {
            return "";
        }
    }

    private static JSONObject itemJson(GameMenuQuickBean bean) throws Exception {
        JSONObject object = new JSONObject();
        object.put("id", bean.getId());
        object.put("name", bean.getName() == null ? "" : bean.getName());
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
     * 导入语义：覆盖全部自定义快捷键。名称允许为空（与界面一致），但必须带按键（codes 或 datas）。
     * v1 文件的 builtin 段（旧内置项状态）被忽略；其 data 段照常导入。
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
                    GameMenuQuickBean bean = new GameMenuQuickBean();
                    bean.setName(object.optString("name", ""));
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
                        continue; // 没有按键的条目没有意义，跳过
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

            sortCustom(result.custom);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    public static final class ImportResult {
        public final List<GameMenuQuickBean> custom = new ArrayList<>();
    }
    // EXTENSION DEVELOPMENT [EXT-QUICK-KEYS-IN-OPERATIONS] [MODIFIED] END
}
