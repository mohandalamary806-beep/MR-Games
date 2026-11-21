# MR Games 0.5.0 — EXE Game Library + Touch Gamepad

MR Games is a branded Android build of the open-source Winlator project. It keeps the Winlator Wine/Box86/Box64 runtime, adds a dedicated MR Games home screen, and leaves the existing Windows desktop, container manager and file manager available for installing games and managing mods.

## What this build changes

- Launches into a new dark / electric-blue **MR Games Home** screen.
- **Add Game (.EXE)** opens the Android file picker, validates the selected Windows executable, creates a dedicated MR Games runtime when needed, then adds a native Winlator desktop shortcut.
- **Play Now** launches the imported shortcut through Winlator's existing Wine/Box64 engine. The game's own executable directory is used as the working directory by the engine.
- Each imported title gets a separate **MR PlayStation** touchscreen profile with a D-pad, two analog sticks, □ △ ✕ ○, L1/L2/R1/R2, L3/R3, Select and Start. The game shortcut stores that profile ID; Winlator loads the controls overlay when the title opens.
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

No universal FPS cap is shown because the engine does not provide a safe, consistent per-title FPS limiter. Resolution and Box64 presets cannot guarantee that every game will run smoothly. Compatibility depends on CPU architecture, GPU and driver support, RAM, thermals, Android version and the individual game.

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

This is an integration patch against the current `brunodev85/winlator-app` source tree. The generated APK must be tested on real devices and with games you own. When the selected EXE is already inside a Winlator shared drive (Downloads or MR Games internal storage), the shortcut points to that original file. If Android exposes only a content URI or the file is outside those mapped drives, MR Games copies the selected EXE into its private shared E: drive; it cannot infer or import every sibling DLL/data file from a single-file picker. In that case, copy the rest of the legally obtained game folder into the same MR Games Library folder before playing. This package itself does not contain game files or make every EXE compatible.

## Upstream and licenses

The runtime is based on Winlator and its Wine / Box86 / Box64 / Mesa / DXVK / VKD3D components. Keep upstream license notices and the source patch with redistributed builds. See `UPSTREAM_LICENSES.md`.


## Direct Launch (no duplicate game copy)

MR Games now links supported EXE files to their original game folder instead of copying the EXE or duplicating a large game directory. Select an EXE whose real filesystem path is visible through Winlator's shared drives (normally the phone's Download folder or MR Games shared storage). Keep the complete game folder in place. If Android's file provider does not expose a mapped filesystem path, the app stops with instructions instead of silently copying only the EXE.


## Mouse touch + PlayStation layout update

- MR PlayStation profiles keep the Windows mouse enabled and the cursor visible.
- A touch in an empty area of the control overlay moves the Windows pointer to that point; a short tap also performs a left-click. This allows pressing OK/Next/Install buttons in Windows dialogs without hiding the gamepad.
- Old MR PlayStation profiles are migrated once to a spaced v2 layout. The migration marker is stored in app preferences, so user-edited v2 positions are not overwritten on every launch.
- D-pad and both sticks occupy the left/right lower edges; the four face buttons use a separated diamond on the right; shoulder buttons stay at the top; Select/Start and L3/R3 sit along the bottom.
- If a game uses its own mouse-lock/cursor mode, Windows/game-specific input settings may still affect cursor behavior.
