# Upstream license and notices

MR Games' Windows compatibility runtime is built from the open-source Winlator Android application:

- Source: https://github.com/brunodev85/winlator-app
- Main project and release information: https://github.com/brunodev85/winlator
- Winlator application license: LGPL-2.1 (retain the upstream `LICENSE` file in redistributed bundles).

Winlator credits upstream projects including Wine, Box86/Box64, Mesa, DXVK, VKD3D and CNC DDraw. Their own licenses and notices remain applicable. MR Games must not remove or obscure those notices.

The GitHub Actions workflow includes the upstream `LICENSE` file and a source-change patch in its build artifact so recipients can inspect the modifications made to the LGPL-covered application source.
