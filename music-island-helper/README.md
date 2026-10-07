# Windows music bridge

The normal Gradle build publishes this helper as a self-contained Windows x64 Native AOT executable and embeds it in the mod. Users do not need .NET installed. The build machine needs a .NET SDK, Visual Studio C++ build tools and a Windows SDK.

`Program.cs` reads Windows Global System Media Transport Controls sessions, metadata, timelines and artwork. Commands carry the displayed session/track identity to prevent controlling a different song after a track change. `MediaArtworkCache.cs` handles artwork caching and refresh. `AudioSourceIdentity.cs` and `PlayerAudioMeter.cs` match the media app to its audio sessions across active render endpoints.

`CoreAudioNative.cs` owns native COM interfaces explicitly. `ProcessLoopback.cs` reads the selected player's output on Windows 11 after its volume controls. `AudioSpectrum.cs` reduces stereo PCM to six frequency bands using the native island's 10 ms windows, fixed sensitivity and beat contrast; samples are immediately discarded. Windows 10 retains a peak-meter fallback. No audio is recorded and the microphone is not used. `BridgeProtocol.cs` declares the JSON response types and source-generated serializers so AOT publishing preserves the protocol without reflection.

AOT/trimming/compiler warnings are treated as errors. Keep the response field names and session/track validation in sync with the Java provider when changing this bridge.

Run `dotnet run --project tests/audio-bridge/AudioBridgeTests.csproj -c Release` from the repository root for spectrum parity and native capture checks. The native test uses its own zero-volume audio session and leaves other players alone.
