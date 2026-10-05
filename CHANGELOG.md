# Changelog

## 1.1.1 — Fabric rendering and mouse controls

- Read the native left mouse button at runtime so pause, previous/next, seeking and outside-click dismissal work on 26.3 as well as earlier 26.x releases.
- Use linear texture sampling and resolution adapted to the actual GUI/display scale for smoother text, controls and rounded corners.
- Preserve complete title and artist glyphs, including descenders, using the original renderer's font metrics and more vertical space.
- Antialias artwork corners before uploading instead of clipping them with a hard-edged native mask.
- Preserve a mouse gesture across the first tick after opening controls; reuse the canvas pixel buffer for uploads.
- Keep the existing Forge 1.1.0 release jars. This patch updates Fabric only.

## 1.1.0 — multi-version beta

- Add Forge adapters for 1.9.4, 1.10.2, 1.11.2, 1.12.2 and 1.13.2 alongside the existing 1.8.9 build.
- Add Fabric builds for the selected stable releases from 1.14.4 through 1.21.11.
- Add one shared Fabric jar for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3, including their GUI initialization and input differences.
- Share the Windows media bridge, animation, artwork palette, playback and gesture logic across adapters.
- Keep OneConfig on 1.8.9 and 1.12.2; provide native settings and configurable Minecraft keybindings on other adapters.
- Embed the accepted MusicIsland logo in every build. Keep menu visibility disabled by default.
- Add version-specific Gradle wrappers, a Windows build-all script, automated build matrix, focused presentation checks and isolated Fabric startup checks.

See `ports/VALIDATION.md` for the tested behavior and remaining runtime limits.
