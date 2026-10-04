# Third-party notices

MusicIsland embeds the **unmodified OneConfig LaunchWrapper bootstrap wrapper 1.0.0-beta17**, developed by Polyfrost and contributors and distributed under the GNU Lesser General Public License version 3. Copies of the LGPL and incorporated GPL are in `licenses/` and the distributed mod jar.

- Source: https://github.com/Polyfrost/OneConfigLoader/tree/main/Wrapper
- Original binary: https://repo.polyfrost.org/releases/cc/polyfrost/oneconfig-wrapper-launchwrapper/1.0.0-beta17/oneconfig-wrapper-launchwrapper-1.0.0-beta17.jar
- Corresponding wrapper sources: https://repo.polyfrost.org/releases/cc/polyfrost/oneconfig-wrapper-launchwrapper/1.0.0-beta17/oneconfig-wrapper-launchwrapper-1.0.0-beta17-sources.jar

You may modify or replace the wrapper and rebuild/relink MusicIsland with an interface-compatible version, and reverse engineer the combined work to debug those library modifications. Change the `embed` dependency in `build.gradle` or replace its wrapper class entries. MusicIsland's application source and build instructions are supplied in this repository. OneConfig itself is downloaded/provided at runtime and retains its own license and notices: https://github.com/Polyfrost/OneConfig.

The native Windows helper incorporates Microsoft .NET runtime and C#/WinRT components under their MIT licenses. Their license texts are in `licenses/`; sources and notices are available at https://github.com/dotnet/runtime and https://github.com/microsoft/CsWinRT. Windows OS APIs are provided by the user's operating system.

The Gradle wrapper is from the Gradle project, distributed under Apache License 2.0: https://github.com/gradle/gradle/tree/v4.4.1. Its license is included in `licenses/`.
