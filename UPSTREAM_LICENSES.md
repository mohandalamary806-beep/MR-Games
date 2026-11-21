# Upstream licenses and attribution

MR Games is a customization layer for the open-source Winlator Android application. The current build workflow checks out `https://github.com/brunodev85/winlator-app` and applies the MR Games launcher files. Preserve upstream license and copyright notices when distributing modified builds.

The runtime may include or download components with their own licenses, including Wine, Box64 / Box86, Mesa / Turnip, DXVK and VKD3D-Proton. Consult the exact upstream revision and its bundled license files for the authoritative component-by-component notices.

The landscape launcher code in this MR Games update is written for this project. Game artwork is not bundled: when online, the launcher performs best-effort title lookup against the public Steam Store search endpoint and loads a corresponding Steam CDN header image. Artwork remains the property of its respective game publishers.

This project is not affiliated with or endorsed by WinHub, GameSir, Steam, Valve, Microsoft, or any game publisher. The UI takes general inspiration from console-style game launchers; it does not include WinHub source code or proprietary assets.
