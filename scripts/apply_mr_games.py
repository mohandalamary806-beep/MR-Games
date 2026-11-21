#!/usr/bin/env python3
"""Patch the upstream Winlator Android source with the MR Games launcher layer.

The patch is intentionally marker-based: if upstream changes the expected code,
the build stops rather than silently shipping an incomplete launcher.
"""
from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path
from PIL import Image, ImageOps

DENSITIES = {"ldpi": 36, "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one upstream marker for {label} in {path}; found {count}.")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def set_app_name(res_dir: Path) -> None:
    pattern = re.compile(r'(<string\b[^>]*\bname=["\']app_name["\'][^>]*>).*?(</string>)', re.DOTALL)
    changed = 0
    for p in res_dir.glob("values*/strings.xml"):
        source = p.read_text(encoding="utf-8")
        updated, n = pattern.subn(r"\1MR Games\2", source)
        if n:
            p.write_text(updated, encoding="utf-8")
            changed += n
    if not changed:
        raise RuntimeError("Could not find app_name in app/src/main/res/values*/strings.xml")


def add_launcher_icon(source_icon: Path, res_dir: Path) -> None:
    icon = Image.open(source_icon).convert("RGBA")
    if icon.width != icon.height:
        raise RuntimeError(f"Launcher icon must be square, got {icon.width}x{icon.height}")
    for density, size in DENSITIES.items():
        folder = res_dir / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        img = ImageOps.fit(icon, (size, size), method=Image.Resampling.LANCZOS)
        img.save(folder / "ic_launcher.png", optimize=True)
        img.save(folder / "ic_launcher_round.png", optimize=True)
    foreground_dir = res_dir / "drawable-nodpi"
    foreground_dir.mkdir(parents=True, exist_ok=True)
    foreground = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    fg = icon.copy()
    fg.thumbnail((760, 760), Image.Resampling.LANCZOS)
    foreground.alpha_composite(fg, ((1024-fg.width)//2, (1024-fg.height)//2))
    foreground.save(foreground_dir / "mr_games_launcher_foreground.png", optimize=True)
    (res_dir / "values").mkdir(parents=True, exist_ok=True)
    (res_dir / "values" / "mr_games_brand.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources><color name="mr_games_icon_background">#06101E</color></resources>
''', encoding="utf-8")
    adaptive_dir = res_dir / "mipmap-anydpi-v26"
    adaptive_dir.mkdir(parents=True, exist_ok=True)
    adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/mr_games_icon_background" />
    <foreground android:drawable="@drawable/mr_games_launcher_foreground" />
</adaptive-icon>
'''
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (adaptive_dir / name).write_text(adaptive, encoding="utf-8")


def update_style(text: str, style_name: str, item_name: str, value: str) -> str:
    p = re.compile(rf'(<style\s+name=["\']{re.escape(style_name)}["\'][^>]*>)(.*?)(</style>)', re.DOTALL)
    m = p.search(text)
    if not m:
        raise RuntimeError(f"Could not find theme {style_name} in styles.xml")
    body = m.group(2)
    item = re.compile(rf'(<item\s+name=["\']{re.escape(item_name)}["\']\s*>).*?(</item>)', re.DOTALL)
    updated, n = item.subn(rf"\1{value}\2", body, count=1)
    if not n:
        raise RuntimeError(f"Could not find {item_name} in {style_name}")
    return text[:m.start(2)] + updated + text[m.end(2):]


def apply_blue_theme(styles_file: Path) -> None:
    text = styles_file.read_text(encoding="utf-8")
    palettes = {
        "AppThemeLight": {
            "colorAccent": "#008FE5", "colorPrimary": "#15547C", "colorPrimaryDark": "#0A2D48",
            "colorControlNormal": "#5C8BA9", "colorPrimaryText": "#10283A", "colorSecondaryText": "#58758B",
            "colorPrimarySurface": "#F2F7FC", "colorSecondarySurface": "#FFFFFF", "colorPrimaryVariant": "#087DBB",
            "colorSecondaryVariant": "#D1E4F2", "colorError": "#D9435C",
        },
        "AppThemeDark": {
            "colorAccent": "#12B9FF", "colorPrimary": "#102942", "colorPrimaryDark": "#06101E",
            "colorControlNormal": "#527A99", "colorPrimaryText": "#ECF7FF", "colorSecondaryText": "#91ABC3",
            "colorPrimarySurface": "#070D16", "colorSecondarySurface": "#111F30", "colorPrimaryVariant": "#168AC5",
            "colorSecondaryVariant": "#193B58", "colorError": "#FF627A",
        },
    }
    for style_name, items in palettes.items():
        for item_name, value in items.items():
            text = update_style(text, style_name, item_name, value)
    styles_file.write_text(text, encoding="utf-8")


def write_java_files(java_dir: Path, payload_dir: Path) -> None:
    java_dir.mkdir(parents=True, exist_ok=True)
    for name in ("MRGamePerformance.java", "MRGamesHomeFragment.java", "MRGameLibrary.java"):
        src = payload_dir / name
        if not src.is_file():
            raise FileNotFoundError(f"Missing packaged Java source: {src}")
        shutil.copy2(src, java_dir / name)


def patch_app_version(path: Path) -> None:
    """Set the MR Games version without incrementing versionCode on every rerun."""
    text = path.read_text(encoding="utf-8")
    name_pattern = re.compile(r'(versionName\s+)["\']([^"\']+)["\']')
    name_match = name_pattern.search(text)
    if not name_match:
        raise RuntimeError(f"Could not find versionName in {path}")

    # The upstream version is bumped once when converting it to this MR Games build.
    # Re-running the patch against the same tree must leave the versionCode unchanged.
    if name_match.group(2) != "0.5.0":
        code_pattern = re.compile(r"(versionCode\s+)([0-9]+)")
        text, count = code_pattern.subn(lambda m: m.group(1) + str(int(m.group(2)) + 1), text, count=1)
        if count != 1:
            raise RuntimeError(f"Could not find versionCode in {path}")

    text, count = name_pattern.subn(r'\g<1>"0.5.0"', text, count=1)
    if count != 1:
        raise RuntimeError(f"Could not set versionName in {path}")
    path.write_text(text, encoding="utf-8")


def _replace_java_method(path: Path, method_pattern: str, replacement: str, label: str) -> None:
    """Replace one Java method using balanced braces, not brittle full-text matching."""
    text = path.read_text(encoding="utf-8")
    match = re.search(method_pattern, text, re.MULTILINE)
    if not match:
        raise RuntimeError(f"Could not locate {label} in {path}")

    open_brace = text.find("{", match.start())
    if open_brace < 0:
        raise RuntimeError(f"Could not locate method body for {label} in {path}")

    depth = 0
    in_string = False
    in_char = False
    escaped = False
    i = open_brace
    while i < len(text):
        ch = text[i]
        if in_string or in_char:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif in_string and ch == '"':
                in_string = False
            elif in_char and ch == "'":
                in_char = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "'":
                in_char = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    text = text[:match.start()] + replacement + text[i + 1:]
                    path.write_text(text, encoding="utf-8")
                    return
        i += 1
    raise RuntimeError(f"Unbalanced braces while replacing {label} in {path}")


def patch_main_activity(path: Path) -> None:
    text = path.read_text(encoding="utf-8")

    # Launch MR Games Home by default, while preserving explicit navigation
    # requests passed by the upstream app (e.g. opening the input-controls editor).
    home_selection = "navigationView.setCheckedItem(R.id.menu_item_mr_games_home);"
    if home_selection not in text:
        new_block = '''            int selectedMenuItemId = intent.getIntExtra("selected_menu_item_id", 0);
            actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_menu);
            if (selectedMenuItemId > 0) {
                onNavigationItemSelected(navigationView.getMenu().findItem(selectedMenuItemId));
                navigationView.setCheckedItem(selectedMenuItemId);
            }
            else {
                showFragment(new MRGamesHomeFragment());
                navigationView.setCheckedItem(R.id.menu_item_mr_games_home);
            }'''
        # Match the current preference-based startup block independent of spacing.
        pattern = re.compile(
            r'(?ms)^(?P<indent>[ \t]*)boolean\s+showShortcutsFirst\s*=.*?'
            r'^(?P=indent)navigationView\.setCheckedItem\(menuItemId\);'
        )
        updated, count = pattern.subn(
            lambda m: new_block.replace("            ", m.group("indent")), text, count=1
        )

        # Fallback for upstream revisions which refactor the preference setup but
        # retain the two navigation calls and selectedMenuItemId variable.
        if count == 0:
            fallback = re.compile(
                r'(?m)^(?P<indent>[ \t]*)onNavigationItemSelected\('
                r'navigationView\.getMenu\(\)\.findItem\(menuItemId\)\);\s*\n'
                r'(?P=indent)navigationView\.setCheckedItem\(menuItemId\);'
            )
            updated, count = fallback.subn(
                lambda m: new_block.replace("            ", m.group("indent")), text, count=1
            )

        if count != 1:
            raise RuntimeError(
                f"Could not identify Winlator's startup navigation block in {path}. "
                "Upstream MainActivity.java has changed; no files were silently modified."
            )
        path.write_text(updated, encoding="utf-8")
        text = updated

    # Add the MR Games Home route to the existing navigation switch.
    if "case R.id.menu_item_mr_games_home:" not in text:
        case_pattern = re.compile(r'(?m)^(?P<indent>[ \t]*)case R\.id\.menu_item_shortcuts:')
        text, count = case_pattern.subn(
            lambda m: (
                f'{m.group("indent")}case R.id.menu_item_mr_games_home:\n'
                f'{m.group("indent")}    showFragment(new MRGamesHomeFragment());\n'
                f'{m.group("indent")}    break;\n'
                f'{m.group("indent")}case R.id.menu_item_shortcuts:'
            ),
            text,
            count=1,
        )
        if count != 1:
            raise RuntimeError(f"Could not find the shortcuts navigation case in {path}")
        path.write_text(text, encoding="utf-8")

    # The balanced-brace replacement survives harmless upstream formatting changes.
    _replace_java_method(
        path,
        r'(?m)^(?P<indent>[ \t]*)public\s+void\s+onBackPressed\s*\(\s*\)\s*\{',
        '''    public void onBackPressed() {
        if (currentFragment != null && currentFragment.isVisible()) {
            if (currentFragment instanceof BaseFileManagerFragment) {
                BaseFileManagerFragment fileManagerFragment = (BaseFileManagerFragment)currentFragment;
                if (fileManagerFragment.onBackPressed()) return;
            }
            if (currentFragment instanceof ContainerDetailFragment) {
                showFragment(new ContainersFragment());
                return;
            }
            if (currentFragment instanceof MRGamesHomeFragment) {
                super.onBackPressed();
                return;
            }
        }
        showFragment(new MRGamesHomeFragment());
    }''',
        "MainActivity.onBackPressed()",
    )

def patch_menu(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "menu_item_mr_games_home" in text:
        return
    pattern = re.compile(r'(<group\s+android:checkableBehavior="single">\s*)(<item[^>]*android:id="@\+id/menu_item_shortcuts"[^>]*/>)')
    updated, count = pattern.subn(r'\1        <item android:icon="@drawable/icon_shortcut" android:id="@+id/menu_item_mr_games_home" android:title="@string/app_name" />\n        \2', text, count=1)
    if count != 1:
        raise RuntimeError(f"Could not find the shortcuts item in {path} to add MR Games Home")
    path.write_text(updated, encoding="utf-8")


def patch_box64(java_dir: Path) -> None:
    preset = java_dir / "box64" / "Box64Preset.java"
    manager = java_dir / "box64" / "Box64PresetManager.java"
    replace_once(preset,
'''    public static final String CUSTOM = "CUSTOM";
    public static final String DEFAULT = PERFORMANCE;''',
'''    public static final String CUSTOM = "CUSTOM";
    public static final String MR_LOW_END = "MR_LOW_END";
    public static final String MR_BALANCED = "MR_BALANCED";
    public static final String MR_HIGH_VISUAL = "MR_HIGH_VISUAL";
    public static final String MR_STABILITY = "MR_STABILITY";
    public static final String DEFAULT = PERFORMANCE;''', "MR Games Box64 preset identifiers")

    replace_once(manager,
'''    public static EnvVars getEnvVars(Context context, String id) {
        EnvVars envVars = new EnvVars();''',
'''    public static EnvVars getEnvVars(Context context, String id) {
        // MR Games labels map to the upstream-tested Box64 configurations.
        if (id.equals(Box64Preset.MR_LOW_END)) id = Box64Preset.PERFORMANCE;
        else if (id.equals(Box64Preset.MR_BALANCED)) id = Box64Preset.INTERMEDIATE;
        else if (id.equals(Box64Preset.MR_HIGH_VISUAL)) id = Box64Preset.INTERMEDIATE;
        else if (id.equals(Box64Preset.MR_STABILITY)) id = Box64Preset.STABILITY;
        EnvVars envVars = new EnvVars();''', "map MR Games Box64 profile IDs")

    replace_once(manager,
'''    public static ArrayList<Box64Preset> getPresets(Context context) {
        ArrayList<Box64Preset> presets = new ArrayList<>();''',
'''    public static ArrayList<Box64Preset> getPresets(Context context) {
        ArrayList<Box64Preset> presets = new ArrayList<>();
        presets.add(new Box64Preset(Box64Preset.MR_LOW_END, "MR Games - Low-End"));
        presets.add(new Box64Preset(Box64Preset.MR_BALANCED, "MR Games - Balanced"));
        presets.add(new Box64Preset(Box64Preset.MR_HIGH_VISUAL, "MR Games - High Visual"));
        presets.add(new Box64Preset(Box64Preset.MR_STABILITY, "MR Games - Stability"));''', "show MR Games Box64 profiles in native spinner")


def patch_container_defaults(java_dir: Path) -> None:
    path = java_dir / "ContainerDetailFragment.java"
    replace_once(path,
'''        loadScreenSizeSpinner(view, isEditMode() ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE);''',
'''        loadScreenSizeSpinner(view, isEditMode() ? container.getScreenSize() : MRGamePerformance.getDefaultScreenSize(context));''',
        "use MR Games adaptive default resolution for new containers")
    replace_once(path,
'''        Box64PresetManager.loadSpinner(sBox64Preset, isEditMode() ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.DEFAULT));''',
'''        Box64PresetManager.loadSpinner(sBox64Preset, isEditMode() ? container.getBox64Preset() : MRGamePerformance.getDefaultBox64Preset(context));''',
        "use MR Games default Box64 profile for new containers")



def patch_touchscreen_mouse_input(java_dir: Path) -> None:
    """Enable visible, direct-touch mouse operation while MR gamepad controls are active."""
    display = java_dir / "XServerDisplayActivity.java"
    replace_once(
        display,
        """        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        touchpadView.setPointerButtonRightEnabled(false);""",
        """        touchpadView.setSensitivity(profile.getCursorSpeed() * globalCursorSpeed);
        boolean isMRPlayStationProfile = profile.getName() != null && profile.getName().startsWith("MR PlayStation - ");
        if (isMRPlayStationProfile) MRGameLibrary.prepareProfileForLaunch(this, profile);
        // Blank overlay areas pass touches to TouchpadView. In MR profiles, a finger
        // moves the Windows cursor to the touched screen point and a tap performs a
        // left-click, so users can dismiss Windows dialogs and press OK buttons.
        touchpadView.setMoveCursorToTouchpoint(isMRPlayStationProfile || preferences.getBoolean("move_cursor_to_touchpoint", false));
        touchpadView.setPointerButtonRightEnabled(false);""",
        "enable touch-to-mouse and tap-to-click for MR PlayStation profiles"
    )
    replace_once(
        display,
        """        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setPointerButtonLeftEnabled(true);""",
        """        touchpadView.setSensitivity(globalCursorSpeed);
        touchpadView.setMoveCursorToTouchpoint(preferences.getBoolean("move_cursor_to_touchpoint", false));
        touchpadView.setPointerButtonLeftEnabled(true);""",
        "restore the user's normal touchpad cursor mode when controls close"
    )


def verify_gamepad_shortcut_support(java_dir: Path) -> None:
    """Verify the real Winlator shortcut/profile APIs without assuming a hardcoded field.

    Shortcut stores arbitrary [Extra Data] keys in a JSONObject. The
    `controlsProfile` key is therefore intentionally NOT declared in
    Shortcut.java; XServerDisplayActivity reads that key through getExtra().
    """
    shortcut_source = (java_dir / "container/Shortcut.java").read_text(encoding="utf-8")
    display_source = (java_dir / "XServerDisplayActivity.java").read_text(encoding="utf-8")
    controls_source = (java_dir / "inputcontrols/InputControlsManager.java").read_text(encoding="utf-8")
    profile_source = (java_dir / "inputcontrols/ControlsProfile.java").read_text(encoding="utf-8")
    element_source = (java_dir / "inputcontrols/ControlElement.java").read_text(encoding="utf-8")
    binding_source = (java_dir / "inputcontrols/Binding.java").read_text(encoding="utf-8")
    manager_source = (java_dir / "container/ContainerManager.java").read_text(encoding="utf-8")
    touchpad_source = (java_dir / "widget/TouchpadView.java").read_text(encoding="utf-8")
    input_view_source = (java_dir / "widget/InputControlsView.java").read_text(encoding="utf-8")

    # Shortcut's [Extra Data] is a generic key/value store, not a fixed schema.
    shortcut_support = all((
        re.search(r'JSONObject\s+extraData\s*=\s*new\s+JSONObject\s*\(', shortcut_source),
        re.search(r'section\.equals\(\"Extra Data\"\)', shortcut_source),
        re.search(r'extraData\.put\(key\s*,\s*value\)', shortcut_source),
        re.search(r'public\s+String\s+getExtra\(String\s+name', shortcut_source),
    ))
    if not shortcut_support:
        raise RuntimeError(
            "Winlator Shortcut no longer supports generic [Extra Data] key/value metadata; "
            "cannot attach a per-game controls profile safely"
        )

    launch_support = all((
        re.search(r'shortcut\s*\.\s*getExtra\(\s*\"controlsProfile\"\s*\)', display_source),
        re.search(r'inputControlsManager\s*\.\s*getProfile\(', display_source),
        "showInputControls(profile)" in display_source,
    ))
    if not launch_support:
        raise RuntimeError(
            "Winlator no longer loads a shortcut's controlsProfile in XServerDisplayActivity"
        )

    touch_mouse_support = all((
        "setMoveCursorToTouchpoint" in touchpad_source,
        "moveCursorToTouchpoint" in touchpad_source,
        "touchpadView.onTouchEvent(event)" in input_view_source,
        "setMoveCursorToTouchpoint(isMRPlayStationProfile" in display_source,
        "MRGameLibrary.prepareProfileForLaunch(this, profile)" in display_source,
        "isCursorVisible()" in display_source,
    ))
    if not touch_mouse_support:
        raise RuntimeError(
            "Winlator touchpad no longer supports direct touch-to-cursor and tap-to-click; "
            "cannot enable mouse interaction safely"
        )

    profile_support = all((
        re.search(r'public\s+ControlsProfile\s+createProfile\(String\s+name\)', controls_source),
        re.search(r'public\s+ControlsProfile\s+getProfile\(int\s+id\)', controls_source),
        re.search(r'public\s+ArrayList<ControlsProfile>\s+getProfiles\(boolean', controls_source),
        "getJSONArray(\"elements\")" in profile_source,
        "Binding.fromString" in profile_source,
        "ControlElement.Type.valueOf" in profile_source,
        "ControlElement.Shape.valueOf" in profile_source,
        "getBindingCount" in element_source,
        "public JSONObject toJSONObject()" in element_source,
    ))
    if not profile_support:
        raise RuntimeError(
            "Winlator input-controls profile API or .icp element format changed; "
            "cannot create the MR PlayStation profile safely"
        )

    required_gamepad_bindings = (
        "GAMEPAD_DPAD_UP", "GAMEPAD_DPAD_RIGHT", "GAMEPAD_DPAD_DOWN", "GAMEPAD_DPAD_LEFT",
        "GAMEPAD_LEFT_THUMB_UP", "GAMEPAD_LEFT_THUMB_RIGHT", "GAMEPAD_LEFT_THUMB_DOWN", "GAMEPAD_LEFT_THUMB_LEFT",
        "GAMEPAD_RIGHT_THUMB_UP", "GAMEPAD_RIGHT_THUMB_RIGHT", "GAMEPAD_RIGHT_THUMB_DOWN", "GAMEPAD_RIGHT_THUMB_LEFT",
        "GAMEPAD_BUTTON_A", "GAMEPAD_BUTTON_B", "GAMEPAD_BUTTON_X", "GAMEPAD_BUTTON_Y",
        "GAMEPAD_BUTTON_L1", "GAMEPAD_BUTTON_L2", "GAMEPAD_BUTTON_R1", "GAMEPAD_BUTTON_R2",
        "GAMEPAD_BUTTON_L3", "GAMEPAD_BUTTON_R3", "GAMEPAD_BUTTON_SELECT", "GAMEPAD_BUTTON_START",
    )
    missing_bindings = [name for name in required_gamepad_bindings if name not in binding_source]
    if missing_bindings:
        raise RuntimeError("Winlator gamepad bindings missing: " + ", ".join(missing_bindings))

    if not re.search(r'public\s+ArrayList<Shortcut>\s+loadShortcuts\(Shortcut\s+selectedFolder\)', manager_source):
        raise RuntimeError("Winlator shortcut library API changed; cannot verify game-library indexing")


def apply(source: Path, icon: Path, payload_dir: Path) -> None:
    app = source / "app"
    java = app / "src/main/java/com/winlator"
    res = app / "src/main/res"
    required = [source / "gradlew", app / "build.gradle", java / "MainActivity.java",
                java / "ContainerDetailFragment.java", java / "container/ContainerManager.java",
                java / "container/Container.java", java / "container/Shortcut.java",
                java / "inputcontrols/InputControlsManager.java", java / "inputcontrols/ControlsProfile.java",
                java / "inputcontrols/Binding.java", java / "inputcontrols/ControlElement.java",
                java / "widget/TouchpadView.java", java / "widget/InputControlsView.java",
                java / "XServerDisplayActivity.java", java / "core/AppUtils.java", java / "core/FileUtils.java",
                java / "core/StringUtils.java", java / "core/WineUtils.java", java / "box64/Box64Preset.java",
                java / "box64/Box64PresetManager.java", res / "values/styles.xml",
                res / "menu/main_menu.xml", app / "src/main/AndroidManifest.xml", icon]
    for p in required:
        if not p.exists():
            raise FileNotFoundError(f"Required upstream file missing: {p}")
    write_java_files(java, payload_dir)
    patch_app_version(app / "build.gradle")
    set_app_name(res)
    add_launcher_icon(icon, res)
    apply_blue_theme(res / "values/styles.xml")
    patch_main_activity(java / "MainActivity.java")
    patch_touchscreen_mouse_input(java)
    patch_menu(res / "menu/main_menu.xml")
    patch_box64(java)
    patch_container_defaults(java)

    # Validate the actual generic [Extra Data] contract and native profile loader.
    # `controlsProfile` is stored as a generic key, so it is not expected to be
    # mentioned in Shortcut.java itself.
    verify_gamepad_shortcut_support(java)

    manifest = (app / "src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    if 'android:name="com.winlator.MainActivity"' not in manifest:
        raise RuntimeError("Unexpected launcher activity; cannot safely brand this upstream revision")
    print("MR Games launcher installed: Home, Library, Windows Desktop / Mod Workshop.")
    print("Device-aware defaults installed for new containers (RAM/CPU/Vulkan heuristic).")
    print("Box64 profile aliases installed; per-game overrides remain in the native Shortcut Settings dialog.")
    print("Add Game imports EXE shortcuts with per-game native PlayStation-style virtual gamepad profiles.")
    print("Each imported title is linked to its own touchscreen profile through Winlator shortcut metadata.")
    print("MR PlayStation profiles show the mouse cursor and support direct touch-to-cursor movement plus tap-to-click.")
    print("The v2 PlayStation layout keeps controls separated around the screen edges and migrates old generated profiles once.")
    print("No generic FPS cap is applied: the upstream engine does not expose a safe universal per-title limiter.")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--icon", required=True, type=Path)
    parser.add_argument("--payload", required=True, type=Path)
    args = parser.parse_args()
    apply(args.source.resolve(), args.icon.resolve(), args.payload.resolve())
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"MR Games patch failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
