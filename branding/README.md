# MusicIsland logo

The accepted mod icon is `MusicIsland.png`: a 512 × 512 PNG containing an angled island capsule with an original sunset album illustration and six pastel waveform bars. It is also embedded as `assets/musicisland/logo.png` in every mod build.

## Files

- `MusicIsland.png`: the accepted distribution icon, 512 x 512.
- `MusicIsland-color.svg`: scalable vector source with named groups.
- `MusicIsland-editable.psd`: five raster layers: background, shadow, capsule, artwork, waveform.
- `layers/`: individual transparent 1024-pixel layers.

## Editing in Paint.NET

Install the [Paint.NET PSD filetype plugin](https://www.psdplugin.com/) and restart Paint.NET. Open `MusicIsland-editable.psd`, then use Save As and choose Paint.NET (`.pdn`) for a native project file. The repository supplies the layered PSD and individual PNG layers; it does not bundle the plugin.

## Provenance and platform requirements

This artwork was designed by Codex using explicitly authored geometric shapes and rendering code; it was not produced with an image-generation model. The sunset artwork is original, not an existing album image. This is still assistant-authored artwork.

CurseForge requests an original square PNG of at least 400 x 400, so the 512 and 1024 exports meet its stated file dimensions.

Modrinth's policy prohibits primarily or fully AI-generated branding images. This assistant-created artwork should therefore not be treated as compliant for a Modrinth project icon.

- https://support.curseforge.com/support/solutions/articles/9000199552-overview-of-the-project-submission-page
- https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai

## Validation

The PSD was read independently with psd-tools: all five layers were recognized, the merged image matched the master PNG exactly, and recompositing the layers produced a root mean square channel difference below 1 on a 0–255 scale. PNG exports are opaque, square, and were visually checked at 512 and 64 pixels.

Older alternate exports in the local workspace are not the distribution icon.
