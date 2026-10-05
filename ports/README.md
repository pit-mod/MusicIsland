# Version adapters

The main project remains the Forge 1.8.9 build. These independent Gradle projects adapt MusicIsland to the final stable release in each later Minecraft family, plus the commonly used 1.20.1 and 1.21.1 releases.

| Project | Minecraft | Build JDK / Gradle |
| --- | --- | --- |
| Root | Forge 1.8.9 | 8 / 4.4.1 |
| `forge-legacy` | Forge 1.9.4, 1.10.2, 1.11.2 | 8 / root wrapper |
| `forge-1.12.2` | Forge 1.12.2 | 8 / root wrapper |
| `forge-1.13.2` | Forge 1.13.2 | 8 / 4.10.3 |
| `fabric` | Fabric 1.14.4–1.21.11; see `targets.json` | 21 / 9.2.0 |
| `fabric-26` | Fabric 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | 25 / 9.5.1 |

Every build requires the .NET 8 SDK, Windows SDK and Visual Studio C++ build tools for the native Windows bridge. Fabric adapter generation also uses Python 3 without external packages. The Gradle JDK and the Minecraft runtime JDK are different for older Fabric versions; `targets.json` records the runtime minimum.

From `forge-legacy`, use `../../gradlew.bat -Pmc=1.10.2 build`. From `forge-1.12.2`, use `../../gradlew.bat build`. From `forge-1.13.2`, use `./gradlew.bat build`. From `fabric`, use `./gradlew.bat -Pmc=1.16.5 build`. From `fabric-26`, use `./gradlew.bat build` to build the shared 26.1–26.3 jar against 26.1.2.

To build all release jars from the repository root:

```powershell
./ports/build-all.ps1 -Jdk8 C:/Java/jdk8 -Jdk21 C:/Java/jdk21 -Jdk25 C:/Java/jdk25
```

Jars are collected in `build/releases/1.1.0`. Each pre-26 jar intentionally declares only the versions it was built for. Minecraft's rendering, mapping and input APIs change between families; widening metadata alone does not make a binary compatible.

## Shared code and checks

The Windows helper and media/animation classes come from the root sources. `common` provides the native texture renderer and settings fallback for adapters without a compatible OneConfig implementation. The original OneConfig settings remain on 1.8.9 and 1.12.2.

`common`'s `check` task verifies presentation, gesture ownership, artwork direction, optimistic controls, RGBA rendering and settings defaults. Run it using `../fabric/gradlew.bat check` from that directory with JDK 21.

Fabric adapters also expose `runSmokeClient`. This launches an isolated development client, loads the actual native texture, opens settings and controls, checks expansion/collapse, and verifies default menu hiding. Its small auxiliary test mod is excluded from release jars. It uses preview playback and does not claim verification of every external music app or modpack. For example, from `fabric-26`: `./gradlew.bat -Pmc=26.3 runSmokeClient`.

Release checks and their limits are documented in `VALIDATION.md`.
