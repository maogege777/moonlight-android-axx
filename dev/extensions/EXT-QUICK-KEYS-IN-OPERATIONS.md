# EXT-QUICK-KEYS-IN-OPERATIONS

- Change type: extension development
- Status: implemented, pending on-device verification
- Added: 2026-10-15
- Purpose: fold the stream menu's "Quick keys" (「快捷键」) feature into the sidebar
  "Operations" (「操作」) panel as a new "Custom quick keys" (「自定义快捷键」) grid group, and add
  export/import of the user's custom quick keys in Settings › Interface & General.

- User-visible behavior:
  - The main menu no longer has a "Quick keys" entry; the sidebar "Operations" panel starts with a
    "Custom quick keys" group holding the 11 built-in Windows shortcuts plus the user's own combos.
  - Grid buttons show an icon plus a name; tapping sends the combination and closes the panel,
    long-pressing opens Edit / Delete. Built-in entries can be deleted too (no per-item restore).
  - The panel's top-right "Add" button opens the same combination editor as before, now with an
    optional SVG icon picker (fixed 19 icons; no selection keeps the default keyboard icon).
  - Settings › Interface & General › Custom quick keys exports all custom entries (icons and order
    included) to a JSON file, and imports a file replacing all custom entries after a confirmation.

- Added files:
  - `app/src/main/java/com/limelight/ui/gamemenu/QuickKeyStore.java`
  - `app/src/main/res/layout/item_game_menu_quick_key.xml`
  - `app/src/main/res/drawable/ic_axi_qk_*.xml` (19 vector icons)
  - `dev/gen_quick_icons.ps1`, `dev/check_resources.ps1`, `dev/check_java_balance.ps1`

- Modified files:
  - `app/src/main/java/com/limelight/ui/gamemenu/GameMenuFragment.java`
  - `app/src/main/java/com/limelight/ui/gamemenu/GameFunctionFragment.java`
  - `app/src/main/java/com/limelight/ui/gamemenu/GameListQuickFragment.java`
  - `app/src/main/java/com/limelight/ui/gamemenu/GameKeyboardUpdateFragment.java`
  - `app/src/main/java/com/limelight/ui/gamemenu/bean/GameMenuQuickBean.java`
  - `app/src/main/java/com/limelight/preferences/StreamSettings.java`
  - `app/src/main/java/com/limelight/preferences/SettingsCatalog.java`
  - `app/src/main/res/layout/dialog_game_menu.xml`
  - `app/src/main/res/layout/dialog_game_menu_function.xml`
  - `app/src/main/res/layout/dialog_game_menu_keyboard_group_add.xml`
  - `app/src/main/res/xml/preferences.xml`

- Storage: unchanged for user entries - SharedPreferences `quick_axi_keyAssemble` with the
  `quick_assemble_key_` prefix, values still `GameMenuQuickBean` JSON (Gson tolerates the new
  `icon`/`sort` fields). Built-in definitions are never persisted; only their hidden state goes to
  `quick_builtin_hidden`, and edits of a built-in entry are stored under the `builtin_` id prefix in
  the same user store. `QuickKeyStore` is the single owner of all of it.

- Sending constraint: a user entry's `codes` are Android key codes, while a built-in entry's
  `datas` are `KeyboardTranslator` VK codes. `GameFunctionFragment.sendQuickKey` picks the channel by
  which field is present, so re-binding a built-in entry actually takes effect. `mergeBuiltinOverride`
  must not copy the built-in `datas` into the override record for the same reason.

- Import semantics: import replaces every custom entry and restores the exported hidden state and any
  edited built-in entries (`ImportResult.builtinOverrides` via `replaceBuiltinOverrides`), because
  `replaceAllCustom` clears the whole user store. Files that are not produced by this build are
  rejected with a message instead of partially applied.

- Verification so far: `dev/check_resources.ps1` (19 icons + 5 layouts parse) and
  `dev/check_java_balance.ps1` (bracket balance on the 8 touched Java files) both pass. A real
  compile requires GitHub Actions `assembleNonRootDebug`; this machine has no JDK or Android SDK.
  On-device checks still needed: edit-a-built-in-icon only, edit-a-built-in-rebind-keys, and
  export → fresh device → import restoring renames and hidden state.
