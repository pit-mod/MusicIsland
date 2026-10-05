# MusicIsland validation

## 1.1.1 Fabric patch

Checked on 2026-10-05. All 11 Fabric artifacts rebuilt; Forge release artifacts remain the original 1.1.0 files. The shared renderer now uses linear GPU sampling, display-scale supersampling, antialiased artwork masks and the original renderer's font metrics with complete vertical glyph bounds.

Focused regressions compare clipped labels against unclipped glyph output (including `g`, `y` and `p`) and verify the high-density surface dimensions. Actual isolated clients on 1.14.4, 1.21.11, 26.1.2 and 26.3 exercise native screen mouse events for pause, previous, next and seeking, reject non-left clicks, and preserve a gesture across consecutive ticks. Settings, collapse and menu hiding are also checked. These tests use simulated preview playback, not a live external player's response.

Expanded 26.3 output was captured through Minecraft's screenshot API and inspected for text, artwork and rounded-edge quality at a large configured scale. The shared 26.x adapter compiled against each of the five 26.x versions still produces identical class hashes; input constants and GPU sampler types are resolved at runtime to accommodate the SDL/RenderPearl changes in 26.3. Runtime checks use OpenGL; Vulkan has not been separately exercised.

One 26.3 development launch exited with a native access violation during Minecraft resource loading before the island was drawn. A fresh launch completed all checks. The earlier incomplete screenshot-check attempt used an outdated screenshot accessor; that auxiliary test was corrected. Neither auxiliary test code nor its screenshot helper enters release jars.

## 1.1.0 multi-version beta

Checked on 2026-10-05, Windows 11 x64 with an NVIDIA RTX 3060. This is a multi-version **beta**: automated checks establish specific behavior, not a guarantee for every music application, modpack or graphics driver.

## Build coverage

All 17 release jars compiled and were packaged with their loader's reobfuscation/remapping step where required:

- Forge: 1.8.9, 1.9.4, 1.10.2, 1.11.2, 1.12.2, 1.13.2.
- Fabric: 1.14.4, 1.15.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.1, 1.20.6, 1.21.1, 1.21.11.
- One Fabric jar for 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.

The Forge 1.12.2 build uses Forge 14.23.5.2847's legacy development artifacts. Forge 1.13.2 uses 25.0.223. These versions' newer runtime combinations have not received independent game startup checks in this pass.

## Actual client startup checks

Every listed **Fabric** version was launched in an isolated development client with Fabric Loader 0.19.5 and its version-specific Fabric API. The check exercised:

- Client entrypoint, registered rendering callbacks and legacy mixins.
- Native texture allocation/upload and preview rendering.
- Manual expansion, immediate playback/track actions and settings screen creation.
- Collapse completion, default menu hiding and native texture cleanup.

1.14.4–1.16.5 ran on Java 8; 1.17.1–1.21.11 ran on Java 21; 26.x ran on Java 25. Checks used the **OpenGL** backend. The 26.x adapter draws through Minecraft's native GUI and texture APIs without direct OpenGL calls, but Vulkan has not been separately exercised here.

All 26 release classes compiled against each of the five 26.x Minecraft/Fabric API combinations had **identical SHA-256 hashes**. Thus the shared jar uses the same bytecode as the adapter tested on each release. Screen access is resolved after Minecraft initializes its GUI object, and key constants are read at runtime to accommodate 26.3's SDL scancodes.

The new Forge ports received compile/package checks; they have **not all been launched in-game**. The pre-existing 1.8.9 implementation remains the original renderer with OneConfig and the accepted logo. No live server or user save was used in the automated startup checks.

## Shared behavior and release packaging

The executable common presentation check passed manual expansion/track-change behavior, continuous resize reversal, seek ownership and track validation, optimistic pause state, directional artwork transitions, artwork palette sampling, RGBA rendering and configuration defaults/clamping. Its compact and expanded output images were inspected.

`verify-artifacts.py` checks the complete 17-jar set, version metadata, artwork icon, matching Windows helper, third-party notices, legacy mixin package/refmap and exclusion of the auxiliary startup-test mod. `SHA256SUMS.txt` accompanies the release assets.

The Windows bridge and media models are shared with the original project. The automated checks simulate playback; they do not independently verify every Spotify, Deezer, SoundCloud or browser behavior, multiplayer HUD interaction, or another mod's rendering hooks. Only the explicitly listed Minecraft versions are declared in jar metadata.
