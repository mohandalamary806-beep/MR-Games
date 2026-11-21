# MR Games 0.8.0 — Full-Folder Import + Exit Diagnostics + Glass UI + FPS HUD

MR Games is a branded Android build of the open-source Winlator project. It keeps the Winlator Wine/Box86/Box64 runtime, adds a dedicated MR Games home screen, and leaves the existing Windows desktop, container manager and file manager available for installing games and managing mods.

## What this build changes

- Launches into a new dark / electric-blue **MR Games Home** screen.
- **Add Game (.EXE)** opens the Android file picker. Unless the EXE is already inside a verified complete import in MR Games Library, the app then asks for the actual game folder and imports the complete folder tree (EXE, DLLs, data files and subfolders) into MR Games shared storage. This avoids treating a standalone EXE as a complete game.
- **Play Now** launches the imported shortcut through Winlator's existing Wine/Box64 engine. The game's own executable directory is used as the working directory by the engine.
- Each imported title gets a separate **MR PlayStation** touchscreen profile with a D-pad, two analog sticks, □ △ ✕ ○, L1/L2/R1/R2, L3/R3, Select and Start. The game shortcut stores that profile ID; Winlator loads the controls overlay when the title opens.
- Imported MR Games shortcuts enable Winlator's native FPS HUD. The compact translucent FPS panel is positioned at the top-center safe area, away from the touchscreen controls.
- If a game process ends, MR Games displays its exit code and basic troubleshooting steps instead of silently closing the game session. The exit dialog has a **View Logs** action, and MR Games game sessions also expose Winlator’s Logs entry in the in-game side menu, with Wine error logging enabled for diagnostics.
- The MR Games home screen uses translucent glass-style cards, soft blue/cyan outlines and a layered midnight-blue background. In the dark theme, Winlator tool panels, popups, content dialogs and preloader surfaces also use matching translucent navy glass panels with cyan outlines.
- **Game Library** opens Winlator's native Desktop shortcut library. Existing shortcuts remain available.
- **Windows Desktop & Mod Workshop** opens the native container list. Run a container to enter its Windows desktop; use its container menu's File Manager to browse game folders and mod files.
- Per-game controller profiles can be edited with Winlator's native input-controls editor. The generated profile is linked per shortcut and is not shared with unrelated games.
- Detects phone model, reported SoC, total RAM, CPU core count, Android version and whether Android reports the Vulkan hardware feature. The recommendation is heuristic, not a benchmark, and Android may not expose the actual GPU model or VRAM.
- Provides Auto, Low-End, Balanced, High Quality and Stability defaults.
- New containers use the selected default resolution and Box64 preset. Existing containers are not overwritten.
- Per-game overrides are already supported by Winlator's native **Game Library → ⋮ → Settings** dialog (resolution, Box64 preset, graphics driver, DX wrapper, controls and related options).
- Keeps the native Winlator desktop and its file-management workflow for mods and Windows tools.

## Device optimization behavior

Auto mode uses a conservative heuristic based on RAM, CPU core count and Vulkan feature reporting. It is a recommendation, not a benchmark. The selected profile is applied as a default for **new containers**:

| MR Games profile | New-container resolution | Box64 mapping |
|---|---:|---|
| Low-End | 640×360 | upstream Performance |
| Balanced | 960×544 | upstream Intermediate |
| High Quality | 1280×720 | upstream Intermediate |
| Stability | 854×480 | upstream Stability |

The resolution can be manually overridden from MR Games Home. Each game can then have its own resolution, Box64 preset, graphics driver, DirectX wrapper and controller configuration from its shortcut settings.

The native FPS HUD displays measured frame rate; it does not cap or boost performance. No universal FPS cap is applied because the engine does not provide a safe, consistent per-title FPS limiter. Resolution and Box64 presets cannot guarantee that every game will run smoothly. Compatibility depends on CPU architecture, GPU and driver support, RAM, thermals, Android version and the individual game.

## GitHub build

The workflow in `.github/workflows/android-apk.yml` checks out the upstream Winlator source, applies the MR Games patch, builds `assembleDebug`, and uploads an artifact named `MR-Games-Windows-Engine-APK`.

Required repository paths:

```text
.github/workflows/android-apk.yml
branding/MR_Games_Icon.png
branding/MR_Games_UI_Concept.png
scripts/apply_mr_games.py
scripts/MRGamesHomeFragment.java
scripts/MRGameLibrary.java
scripts/MRGamePerformance.java
```

Do not upload proprietary game files, Windows system files, installers or container/rootfs data to GitHub. Upload only the app source customizations.

## Important

This is an integration patch against the current `brunodev85/winlator-app` source tree. The generated APK must be tested on real devices and with games you own. When the selected EXE is already inside a verified complete import in MR Games Library, the shortcut points to that imported file. Older or incomplete EXE-only copies are not trusted; for any other location—including Downloads—MR Games asks you to select the containing game folder and copies the complete folder tree into its private shared E: drive (up to 20 GB and 30,000 files per import). Select the actual game folder, not the whole storage root. This package itself does not contain game files or make every EXE compatible. The exit dialog and Logs menu improve diagnosis but cannot guarantee that an unsupported Windows game will run. The glass styling is designed for the dark theme; selecting the light theme retains its light surfaces.

## Upstream and licenses

Imported folders are staged first and moved into the library only after the EXE is found; failed imports are cleaned up. Each imported copy receives a unique folder name, so games whose main executable is called `setup.exe` do not overwrite one another. The import safety limits are 20 GB and 30,000 files per game.

The runtime is based on Winlator and its Wine / Box86 / Box64 / Mesa / DXVK / VKD3D components. Keep upstream license notices and the source patch with redistributed builds. See `UPSTREAM_LICENSES.md`.
