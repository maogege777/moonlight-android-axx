# EXT-QUICK-KEYS-IN-OPERATIONS

- Change type: extension development
- Status: implemented (v4), pending on-device verification
- Added: 2026-10-15 · v4 revision: 2026-10-16
- Purpose: fold the stream menu's "Quick keys" (「快捷键」) feature into the sidebar
  "Operations" (「操作」) panel as a "Custom quick keys" (「自定义快捷键」) grid group, and add
  export/import of the user's custom quick keys in Settings › Interface & General.

## v4 changes (user-confirmed, 2026-10-16)

1. **Default keys are seeded data, not injected built-ins.** On the first run after install the
   app writes one default group (10 common Windows combos, empty names, fixed ids with the
   `quick_assemble_key_seed_` prefix) into the normal user store, once (guarded by a `seeded`
   flag in the meta file `quick_axi_keyAssemble_meta`). Afterwards they are ordinary entries:
   editable, deletable, exported and imported like anything the user adds. Import no longer
   "resets" anything - it replaces everything, and a successful import also marks the store as
   seeded so the default group is never appended again.
2. **The editor's reset button is "清空按键" (clear keys only)** - it clears the captured
   combination but keeps the entered name.
3. **Names may be empty.** Saving a quick key without a name is allowed (virtual/gamepad
   button combos still require one). A grid button with an empty name shows only the icon plus
   the combination text (desc); the editor no longer auto-overwrites the name field when keys
   are tapped (keyFrom==1 only).
4. **Shared dialog panels are opaque.** `bg_update_dialog_panel.xml` (shared by 8 dialogs incl.
   the import/export confirmations) changed from `#61262626` to `#FF262626`.
5. **Tapping a quick key closes the whole menu stack**, not just the Operations panel:
   `BaseGameMenuFragmentDialog.dismissAllDialogs()` dismisses every live menu dialog.

## User-visible behavior (current)

- The main menu has no "Quick keys" entry; the sidebar "Operations" panel starts with a
  "自定义快捷键" group holding the seeded default group plus the user's own combos.
- Grid buttons show an icon, the name when one was entered, and the combination text below;
  tapping sends the combination and closes all menus, long-pressing opens Edit / Delete.
- The panel's top-right "Add" button opens the combination editor with the optional SVG icon
  picker (fixed 19 icons; no selection keeps the default keyboard icon).
- Settings › Interface & General › Custom quick keys exports every entry (icons and order
  included) to a JSON file, and imports a file replacing all entries after a confirmation.
- "禁用默认的特殊按键指令" hides seeded entries (they remain in the store and in exports) and
  prevents seeding on a fresh device.

## Files

- Added: `QuickKeyStore.java`, `item_game_menu_quick_key.xml`, `ic_axi_qk_*.xml` (19 vectors),
  `dev/gen_quick_icons.ps1`, `dev/check_resources.ps1`, `dev/check_java_balance.ps1`
- Modified: `GameMenuFragment.java`, `GameFunctionFragment.java`, `GameListQuickFragment.java`,
  `GameKeyboardUpdateFragment.java`, `bean/GameMenuQuickBean.java`, `StreamSettings.java`,
  `SettingsCatalog.java`, `Game.java` (seed hook in onCreate),
  `BaseFragmentDialog/BaseGameMenuFragmentDialog.java` (dismissAllDialogs),
  `dialog_game_menu.xml`, `dialog_game_menu_function.xml`, `dialog_game_menu_keyboard_group_add.xml`,
  `bg_update_dialog_panel.xml`, `preferences.xml`

## Storage

- User entries incl. the seeded group: SharedPreferences `quick_axi_keyAssemble`, key prefix
  `quick_assemble_key_`, values `GameMenuQuickBean` JSON (Gson tolerates `icon`/`sort`).
  Seeded ids use the fixed `quick_assemble_key_seed_` prefix.
- `quick_axi_keyAssemble_meta` holds the one-shot `seeded` flag.
- v3 leftovers no longer read or written: the `builtin_` override records (skipped on load, ids
  rewritten on import) and `quick_builtin_hidden`. Old installs keep their user entries; the old
  injected built-ins simply disappear and the seeded group appears instead (only when the store
  has no user entries and seeding has not happened).

## Sending constraint

- A user entry's `codes` are Android key codes (same source as the editor's on-screen keyboard
  tags); `GameFunctionFragment.sendQuickKey` sends them through `Game.keyboardEvent`, which
  translates to VK codes via `KeyboardTranslator` - the seeded group therefore behaves exactly
  like the old built-in VK list. Entries imported from old files that only carry `datas` still
  take the VK path.

## Import/export format

- v2 export: `{type, version:2, exportedAt, data:[...]}` only - what you export is what you
  import, no separate built-in state. `name` may be empty; entries without any keys are skipped.
- v1 files still import: their `data` array is read, their `builtin` section is ignored.

## Verification so far

`dev/check_resources.ps1` (19 icons + 6 layouts/drawables parse) and `dev/check_java_balance.ps1`
(9 touched Java files) both pass. A real compile requires GitHub Actions `assembleNonRootDebug`;
this machine has no JDK or Android SDK. On-device checks still needed: first-run seeding on a
fresh install and on an old install with existing user keys, empty-name buttons, 清空按键 keeping
the name, opaque dialogs over the game画面, tap closing the full menu stack, export → fresh
device → import round-trip.
