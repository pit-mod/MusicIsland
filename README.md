# MusicIsland

A standalone music HUD for **Minecraft Forge 1.8.9**, configured with **OneConfig**. Includes the MusicIsland artwork, Unicode titles, artwork-colored audio visualizer, smooth compact/expanded transitions, and immediate animated playback controls.

[Download the latest jar](https://github.com/pit-mod/MusicIsland/releases/latest)

## Install

1. Use Minecraft 1.8.9 with Forge on **Windows 10 version 1809 or newer / Windows 11, x64**.
2. Put `MusicIsland-1.0.0.jar` in your instance's `mods` folder.
3. Start Minecraft. The bundled official OneConfig loader installs OneConfig if needed; allow internet access on first launch.
4. Play music in an app or browser that exposes a Windows media session.

No Nezur key or installed .NET runtime is needed. This mod only contains MusicIsland, its rendering utilities, and its Windows media bridge. It has no combat modules or custom settings click GUI.

## Settings and controls

- **Mods > MusicIsland > Config** opens its OneConfig settings, including from the main menu.
- **`/musicisland`** opens the same settings; MusicIsland also appears in OneConfig's mod list.
- **M** opens the island's playback controls. The key is configurable in OneConfig.
- **Down arrow** expands or closes the island; **Up** plays/pauses; **Left/Right** selects previous/next. Arrow controls can be disabled.
- Click the progress bar to seek. Click outside the controls or press Escape to return to the game.
- Adjust scale, top/horizontal position, compact title, visualizer, motion, source preference, auto-collapse and paused visibility in OneConfig. Preview mode works without a media app.

The panel expands only when requested. Track changes animate its artwork and title without expanding it. Commands are validated against the displayed session and track before execution. Metadata, artwork, seek and transport availability depend on what the media app publishes to Windows. The visualizer reads audio peaks from matching application sessions; it does not record audio or use the microphone.

## Build from source

Use **JDK 8** for Gradle, a **.NET SDK supporting .NET 8 Native AOT**, and **Visual Studio C++ build tools with a Windows SDK**. On Windows:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk8'
.\gradlew.bat build
```

The normal Gradle build publishes the Windows bridge from `music-island-helper`, embeds its executable, and produces the reobfuscated mod in `build/libs/MusicIsland-1.0.0.jar`. There are no separate overlay source files or manual jar patches. ForgeGradle 2.1 uses Gradle 4.4.1 for this legacy Minecraft version.

Only the small official OneConfig bootstrap wrapper is embedded. The OneConfig library and UniversalCraft are compile-only dependencies and are provided by OneConfig at runtime.

## Third-party components

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `licenses/` for the OneConfig loader and native runtime notices. MusicIsland is independent of Apple, Spotify, Deezer, SoundCloud, YouTube, Mojang and Microsoft.
