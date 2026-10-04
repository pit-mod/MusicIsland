using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Windows.Media.Control;
using Windows.Storage.Streams;

// Fresh GSMTC bridge. Requests are serialized, commands validate BOTH session and track.
// No media keys, process-name guesses, network access, or player credentials.
internal sealed class SessionEntry
{
    internal readonly GlobalSystemMediaTransportControlsSession Session;
    internal readonly string Id = Guid.NewGuid().ToString("N");
    internal readonly MediaArtworkCache Artwork = new();
    internal SessionEntry(GlobalSystemMediaTransportControlsSession session) { Session = session; }
}
internal static class Program
{
    private static GlobalSystemMediaTransportControlsSessionManager? manager;
    private static readonly List<SessionEntry> entries = new();
    private static string stage = "startup";
    private static PlayerAudioMeter? meter;
    private static string meterSource = "";
    private static bool meterPlaying;
    private static string Text(JsonElement r, string key) => r.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() ?? "" : "";
    private static string Track(GlobalSystemMediaTransportControlsSessionMediaProperties m) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(m.Title + "\0" + m.Artist + "\0" + m.AlbumTitle + "\0" + m.TrackNumber)));
    [MTAThread]
    private static void Main()
    {
        // WinExe has redirected handles but no console. Do not call SetConsoleCP.
        Console.SetIn(new StreamReader(Console.OpenStandardInput(), new UTF8Encoding(false)));
        Console.SetOut(new StreamWriter(Console.OpenStandardOutput(), new UTF8Encoding(false)) { AutoFlush = true });
        string? line;
        while ((line = Console.In.ReadLine()) != null)
        {
            if (line.Length > 8192) break;
            long id = 0;
            try
            {
                using var doc = JsonDocument.Parse(line);
                var r = doc.RootElement;
                id = r.GetProperty("id").GetInt64();
                var result = Handle(r);
                Console.WriteLine(JsonSerializer.Serialize(new BridgeSuccess(id, true, result), BridgeJsonContext.Default.BridgeSuccess));
            }
            catch (Exception e)
            {
                // Only exception type crosses the process boundary, never command payloads.
                Console.WriteLine(JsonSerializer.Serialize(new BridgeFailure(id, false, e is TimeoutException ? "Player timed out" : e.GetType().Name, stage, e.HResult.ToString("X8")), BridgeJsonContext.Default.BridgeFailure));
            }
            Console.Out.Flush();
        }
        meter?.Dispose();
    }
    // Keep WinRT projections on one MTA thread across requests. Async Main can resume
    // on a different apartment and make cached sessions fail with RPC_E_WRONG_THREAD.
    private static T Win<T>(Windows.Foundation.IAsyncOperation<T> operation) => operation.AsTask().WaitAsync(TimeSpan.FromSeconds(6)).GetAwaiter().GetResult();
    private static object Handle(JsonElement r)
    {
        if (Text(r,"op") == "meter") {
            stage="audio meter";
            try { meter ??= new PlayerAudioMeter(); return meter.Read(meterSource,meterPlaying); }
            catch { return new MeterReading(false, 0f, meterSource); }
        }
        stage = "manager"; manager ??= Win(GlobalSystemMediaTransportControlsSessionManager.RequestAsync());
        stage = "sessions";
        var sessions = manager.GetSessions();
        entries.RemoveAll(e => !sessions.Any(s => s.Equals(e.Session)));
        foreach (var s in sessions) if (!entries.Any(e => e.Session.Equals(s))) entries.Add(new SessionEntry(s));
        string op = Text(r, "op");
        if (op == "list") return entries.Select(e => new SessionSummary(e.Id, e.Session.SourceAppUserModelId, e.Session.GetPlaybackInfo().PlaybackStatus.ToString())).ToArray();
        if (op != "poll")
        {
            var e = entries.FirstOrDefault(e => e.Id == Text(r, "session"));
            if (e == null) return new CommandResult(false, "Session replaced");
            var m = Win(e.Session.TryGetMediaPropertiesAsync());
            if (Track(m) != Text(r, "track")) return new CommandResult(false, "Track replaced");
            var p = e.Session.GetPlaybackInfo(); var c = p.Controls; bool accepted = false;
            switch (op)
            {
                case "play": if (c.IsPlayEnabled) accepted = Win(e.Session.TryPlayAsync()); break;
                case "pause": if (c.IsPauseEnabled) accepted = Win(e.Session.TryPauseAsync()); break;
                case "previous": if (c.IsPreviousEnabled) accepted = Win(e.Session.TrySkipPreviousAsync()); break;
                case "next": if (c.IsNextEnabled) accepted = Win(e.Session.TrySkipNextAsync()); break;
                case "seek":
                    var t = e.Session.GetTimelineProperties();
                    if (c.IsPlaybackPositionEnabled && t.EndTime > t.StartTime && r.TryGetProperty("seconds", out var secs) && secs.TryGetDouble(out double seconds) && double.IsFinite(seconds))
                    {
                        double lower = Math.Max(t.StartTime.TotalSeconds, t.MinSeekTime.TotalSeconds);
                        double upper = t.MaxSeekTime > t.MinSeekTime ? Math.Min(t.EndTime.TotalSeconds, t.MaxSeekTime.TotalSeconds) : t.EndTime.TotalSeconds;
                        accepted = Win(e.Session.TryChangePlaybackPositionAsync((long)(Math.Clamp(seconds + t.StartTime.TotalSeconds, lower, Math.Max(lower, upper)) * TimeSpan.TicksPerSecond)));
                    }
                    break;
                case "open":
                    if (e.Session.SourceAppUserModelId.Contains("spotify", StringComparison.OrdinalIgnoreCase))
                        accepted = Win(Windows.System.Launcher.LaunchUriAsync(new Uri("spotify:")));
                    break;
            }
            return new CommandResult(accepted, accepted ? "" : "Control unavailable or rejected");
        }
        string preference = Text(r, "preference");
        stage = "selection"; var chosen = entries.OrderByDescending(e => Score(e, preference)).FirstOrDefault();
        if (chosen == null) { meterSource="";meterPlaying=false;return new UnavailableData(); }
        stage = "metadata"; var media = Win(chosen.Session.TryGetMediaPropertiesAsync());
        string track = Track(media);
        long artNow = Environment.TickCount64;
        chosen.Artwork.ObserveTrack(track, artNow);
        if (media.Thumbnail != null && chosen.Artwork.ShouldRead(artNow))
        {
            chosen.Artwork.Reading(artNow);
            try
            {
                using var stream = Win(media.Thumbnail.OpenReadAsync());
                if (stream.Size is > 0 and <= 1048576)
                {
                    using var reader = new DataReader(stream);
                    Win(reader.LoadAsync((uint)stream.Size));
                    byte[] bytes = new byte[stream.Size]; reader.ReadBytes(bytes);
                    chosen.Artwork.Accept(bytes, Environment.TickCount64);
                }
            }
            catch { /* Metadata remains usable when the thumbnail provider fails. */ }
        }
        stage = "timeline"; var info = chosen.Session.GetPlaybackInfo(); var controls = info.Controls; var timeline = chosen.Session.GetTimelineProperties();
        bool playing = info.PlaybackStatus == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing;
        meterSource=chosen.Session.SourceAppUserModelId;meterPlaying=playing;
        double rate = info.PlaybackRate ?? 1;
        // Timeline Position is stamped at LastUpdatedTime, not at this polling request.
        double position = Math.Max(0, (timeline.Position - timeline.StartTime).TotalSeconds);
        double age = (DateTimeOffset.UtcNow - timeline.LastUpdatedTime).TotalSeconds;
        if (playing && double.IsFinite(rate) && timeline.LastUpdatedTime.Year >= 2000 && age >= 0 && age < 86400) position += age * rate;
        double duration = Math.Max(0, (timeline.EndTime - timeline.StartTime).TotalSeconds);
        if (duration > 0) position = Math.Min(position, duration);
        bool sendArt = chosen.Artwork.Hash != Text(r, "knownArt");
        return new PollData {
            available = true, session = chosen.Id, track = track, source = chosen.Session.SourceAppUserModelId,
            title = media.Title, artist = media.Artist, album = media.AlbumTitle,
            playing = playing, position = position, duration = duration, rate = rate, stamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(), timelineStamp = timeline.LastUpdatedTime.ToUnixTimeMilliseconds(),
            artHash = chosen.Artwork.Hash, art = sendArt ? chosen.Artwork.Data : "",
            play = controls.IsPlayEnabled, pause = controls.IsPauseEnabled,
            previous = controls.IsPreviousEnabled, next = controls.IsNextEnabled, seek = controls.IsPlaybackPositionEnabled && duration > 0,
            open = chosen.Session.SourceAppUserModelId.Contains("spotify", StringComparison.OrdinalIgnoreCase)
        };
    }
    private static int Score(SessionEntry e, string preference)
    {
        string source = e.Session.SourceAppUserModelId;
        var status = e.Session.GetPlaybackInfo().PlaybackStatus;
        int score = status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Playing ? 100 : status == GlobalSystemMediaTransportControlsSessionPlaybackStatus.Paused ? 50 : 0;
        if (preference.Equals("Spotify", StringComparison.OrdinalIgnoreCase) && source.Contains("spotify", StringComparison.OrdinalIgnoreCase)) score += 200;
        if (preference.Equals("Current", StringComparison.OrdinalIgnoreCase) && manager!.GetCurrentSession()?.Equals(e.Session) == true) score += 200;
        return score;
    }
}
