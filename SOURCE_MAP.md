# Source for each Minecraft version

Every supported version is built from this repository. The source is public under the [MIT license](LICENSE). Adapters share the media bridge and animation code; separate repositories or branches for every Minecraft release are not required.

## Download

- **Latest development source:** [main branch](https://github.com/pit-mod/MusicIsland/tree/main), or GitHub's **Code → Download ZIP**.
- **Fabric 1.1.1 release source:** [v1.1.1](https://github.com/pit-mod/MusicIsland/tree/v1.1.1). The release's automatic **Source code (zip)** and **Source code (tar.gz)** downloads contain the entire repository at that tag.
- **Original Forge 1.1.0 release source:** [v1.1.0](https://github.com/pit-mod/MusicIsland/tree/v1.1.0). The Forge jars included in the 1.1.1 download set are unchanged 1.1.0 files; use this tag to match their release source.

Download or clone the **whole repository**, because each adapter depends on shared files outside its own folder. To check out the Fabric patch:

```powershell
git clone https://github.com/pit-mod/MusicIsland.git
cd MusicIsland
git checkout v1.1.1
```

For the original Forge release, use `git checkout v1.1.0` instead. To work on current changes, use `git checkout main`.

## Choose a build

On Windows, set `JAVA_HOME` to the build JDK listed below, enter the project folder, and run its command. Every project builds the Windows bridge and requires the **.NET 8 SDK**, **Visual Studio C++ build tools** and a **Windows SDK**. Fabric adapter generation also requires **Python 3** on `PATH`.

| Minecraft | Loader | Project/source folder | Build JDK | Command from that folder |
| --- | --- | --- | --- | --- |
| 1.8.9 | Forge | [Repository root](./) / [src/main/java](src/main/java) | 8 | `.\gradlew.bat build` |
| 1.9.4 | Forge | [ports/forge-legacy](ports/forge-legacy) | 8 | `..\..\gradlew.bat -Pmc=1.9.4 build` |
| 1.10.2 | Forge | [ports/forge-legacy](ports/forge-legacy) | 8 | `..\..\gradlew.bat -Pmc=1.10.2 build` |
| 1.11.2 | Forge | [ports/forge-legacy](ports/forge-legacy) | 8 | `..\..\gradlew.bat -Pmc=1.11.2 build` |
| 1.12.2 | Forge | [ports/forge-1.12.2](ports/forge-1.12.2) | 8 | `..\..\gradlew.bat build` |
| 1.13.2 | Forge | [ports/forge-1.13.2](ports/forge-1.13.2) | 8 | `.\gradlew.bat build` |
| 1.14.4 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.14.4 build` |
| 1.15.2 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.15.2 build` |
| 1.16.5 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.16.5 build` |
| 1.17.1 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.17.1 build` |
| 1.18.2 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.18.2 build` |
| 1.19.4 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.19.4 build` |
| 1.20.1 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.20.1 build` |
| 1.20.6 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.20.6 build` |
| 1.21.1 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.21.1 build` |
| 1.21.11 | Fabric | [ports/fabric](ports/fabric) | 21 | `.\gradlew.bat -Pmc=1.21.11 build` |
| 26.1–26.3 | Fabric | [ports/fabric-26](ports/fabric-26) | 25 | `.\gradlew.bat build` |

The final row produces **one shared jar** covering 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. The release binary is compiled against 26.1.2. Add `-Pmc=26.3`, for example, to compile against that API for development. The build JDK differs from the game's minimum runtime on older Fabric releases; see [ports/fabric/targets.json](ports/fabric/targets.json).

## Shared and generated source

- [src/main/java](src/main/java) contains the original 1.8.9 mod, renderer and shared media/animation models.
- [music-island-helper](music-island-helper) contains the Windows media bridge's C# source and project file.
- [ports/common/src/main/java](ports/common/src/main/java) contains the portable canvas, controller and fallback settings used by newer adapters.
- Legacy Forge builds generate version-specific copies through their Gradle `prepareCompatibilitySource` task. The transformations are committed in each project's `build.gradle`.
- Fabric 1.14.4–1.21.11 uses the [canonical adapter](ports/fabric/src/main/java), [templates](ports/fabric/templates) and [generate-adapter.py](ports/fabric/generate-adapter.py). The selected version determines the generated API differences.
- Fabric 26.x uses the [shared native adapter](ports/fabric-26/src/main/java), with runtime handling for input, GUI and GPU sampler differences.

Generated Java files appear under each project's `build/` directory after running its normal build. They are reproducible outputs; the templates, generators and shared source are all committed. Compiled jars appear in that project's `build/libs/` or `build/<minecraft-version>/libs/`. The [build-all script](ports/build-all.ps1) collects the complete set.

See [validation](ports/VALIDATION.md) for compile coverage and actual client checks.
