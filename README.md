# MusicIsland

A standalone music HUD for Minecraft, with Forge builds for older versions and Fabric builds for newer versions. Includes album artwork, Unicode titles, an artwork-colored audio visualizer, smooth compact/expanded transitions, and immediate animated playback controls.

[Download the multi-version beta](https://github.com/pit-mod/MusicIsland/releases/tag/v1.1.0)

## Install

1. Use a supported Minecraft version on **Windows 10 version 1809 or newer / Windows 11, x64**.
2. Download the jar matching your Minecraft version and loader, and put it in your instance's `mods` folder. Install only one MusicIsland jar.
3. For Fabric, install Fabric Loader **0.19.5 or newer** and the Fabric API release matching Minecraft. Forge 1.8.9 and 1.12.2 bundle the official OneConfig bootstrap; allow internet access on their first launch.
4. Play music in an app or browser that exposes a Windows media session.

This mod only contains MusicIsland, its rendering utilities, settings and its Windows media bridge.

## Supported releases

| Loader | Minecraft releases | Settings |
| --- | --- | --- |
| Forge | 1.8.9, 1.12.2 | OneConfig |
| Forge | 1.9.4, 1.10.2, 1.11.2, 1.13.2 | Native settings fallback |
| Fabric | 1.14.4, 1.15.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4 | Native settings fallback |
| Fabric | 1.20.1, 1.20.6, 1.21.1, 1.21.11 | Native settings fallback |
| Fabric | **26.1, 26.1.1, 26.1.2, 26.2, 26.3 — one shared jar** | Native settings fallback |

This targets the final stable release of each family rather than every historical patch or snapshot. Extra 1.20.1 and 1.21.1 builds cover common modpack versions. Use Java 8 for Forge and Fabric through 1.16.5, Java 16+ for 1.17.1, Java 17+ for 1.18.2–1.20.1, Java 21+ for 1.20.6–1.21.11 and **Java 25+ for 26.x**. See [validation and limitations](ports/VALIDATION.md).

## Settings and controls

- **Mods > MusicIsland > Config** opens settings on Forge, including from the main menu.
- **`/musicisland`** opens settings. OneConfig builds also appear in OneConfig's mod list; fallback builds offer **F8** as a settings shortcut while playing.
- **M** opens the island's playback controls. Configure keys in OneConfig or Minecraft Options > Controls, depending on the build.
- **Down arrow** expands or closes the island; **Up** plays/pauses; **Left/Right** selects previous/next. Arrow controls can be disabled.
- Click the progress bar to seek. Click outside the controls or press Escape to return to the game.
- Adjust scale, top/horizontal position, compact title, visualizer, motion, source preference, auto-collapse and paused visibility in settings. Preview mode works without a media app.
- The island appears only while a world and player are loaded by default. Enable **Appearance > Show in Minecraft menus** to allow it on the title screen, server browser and other menus outside a game. Settings remain accessible from the Forge Mods menu either way.

The panel expands only when requested. Track changes animate its artwork and title without expanding it. Commands are validated against the displayed session and track before execution. Metadata, artwork, seek and transport availability depend on what the media app publishes to Windows. The visualizer reads audio peaks from matching application sessions; it does not record audio or use the microphone.

## Build from source

Use **JDK 8** for Gradle, a **.NET SDK supporting .NET 8 Native AOT**, and **Visual Studio C++ build tools with a Windows SDK**. On Windows:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk8'
.\gradlew.bat build
```

The root Gradle build publishes the Windows bridge from `music-island-helper`, embeds its executable, and produces the Forge 1.8.9 mod in `build/libs/MusicIsland-1.1.0.jar`. ForgeGradle 2.1 uses Gradle 4.4.1 for this legacy Minecraft version. See [ports/README.md](ports/README.md) for each version's wrapper, the shared sources, checks and a script to build every release jar.

OneConfig builds embed only the small official bootstrap wrapper. The OneConfig library and UniversalCraft are compile-only dependencies and are provided by OneConfig at runtime. Fallback builds do not require OneConfig.

## Third-party components

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `licenses/` for the OneConfig loader and native runtime notices. MusicIsland is independent of Apple, Spotify, Deezer, SoundCloud, YouTube, Mojang and Microsoft.
