# Changelog

## 1.1.0 — multi-version beta

- Add Forge adapters for 1.9.4, 1.10.2, 1.11.2, 1.12.2 and 1.13.2 alongside the existing 1.8.9 build.
- Add Fabric builds for the selected stable releases from 1.14.4 through 1.21.11.
- Add one shared Fabric jar for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3, including their GUI initialization and input differences.
- Share the Windows media bridge, animation, artwork palette, playback and gesture logic across adapters.
- Keep OneConfig on 1.8.9 and 1.12.2; provide native settings and configurable Minecraft keybindings on other adapters.
- Embed the accepted MusicIsland logo in every build. Keep menu visibility disabled by default.
- Add version-specific Gradle wrappers, a Windows build-all script, automated build matrix, focused presentation checks and isolated Fabric startup checks.

See `ports/VALIDATION.md` for the tested behavior and remaining runtime limits.
