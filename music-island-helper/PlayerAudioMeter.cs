using System.Diagnostics;

internal sealed class PlayerAudioMeter : IDisposable
{
    private readonly AudioEnumerator enumerator = new();
    private readonly List<AudioCom> devices = new();
    private readonly List<(AudioCom Meter, AudioCom? Volume, AudioCom Endpoint, int Pid)> meters = new();
    private readonly ProcessLoopback loopback = new();
    private string source = "";
    private DateTime refresh, retry;
    private int meterPid, root, target;
    internal MeterReading Read(string requestedSource, bool playing)
    {
        try
        {
            if (source != requestedSource) { Clear(); source = requestedSource; refresh = retry = default; target = 0; }
            if (!playing || string.IsNullOrEmpty(source))
            {
                loopback.Dispose(); return new MeterReading(meters.Count > 0, 0, source, new float[6], true);
            }
            if (DateTime.UtcNow >= refresh)
            {
                ClearMeters(); refresh = DateTime.UtcNow.AddSeconds(2);
                var identities = new Dictionary<int, (string Name, string AppId)>();
                using var endpoints = enumerator.DeviceEnumerator.ActiveRenderDevices();
                for (int d = 0, count = endpoints.Count(); d < count; d++)
                {
                    var device = endpoints.Item(d); devices.Add(device);
                    using var manager = device.SessionManager(); using var sessions = manager.Sessions();
                    var endpoint = device.Activate(new Guid("5CDF2C82-841E-4546-9722-0CF74078229A")); devices.Add(endpoint);
                    for (int i = 0, sessionCount = sessions.Count(); i < sessionCount; i++)
                    {
                        using var session = sessions.Item(i); AudioCom? peakMeter = null, volume = null;
                        try
                        {
                            int pid = session.ProcessId();
                            if (!identities.TryGetValue(pid, out var identity))
                            {
                                using var process = Process.GetProcessById(pid);
                                identity = (process.ProcessName, AudioSourceIdentity.ApplicationId(pid)); identities[pid] = identity;
                            }
                            if (!AudioSourceIdentity.Matches(source, identity.Name, identity.AppId)) continue;
                            peakMeter = session.Query(new Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064")); if (peakMeter == null) continue;
                            volume = session.Query(new Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"));
                            meters.Add((peakMeter, volume, endpoint, pid)); peakMeter = volume = null;
                        }
                        catch { /* Disappearing sessions must not hide other players. */ }
                        finally { peakMeter?.Dispose(); volume?.Dispose(); }
                    }
                }
            }
            float best = -1, raw = 0, outputGain = 0; int selected = 0;
            foreach (var meter in meters)
            {
                try
                {
                    float peak = meter.Meter.Peak(), db = meter.Endpoint.EndpointDecibels(), volume = meter.Volume?.SessionVolume() ?? 1;
                    if (!float.IsFinite(peak) || !float.IsFinite(db) || !float.IsFinite(volume)) continue;
                    float gain = AudioSpectrum.Gain(db, meter.Endpoint.EndpointMuted());
                    float audible = Math.Clamp(peak, 0, 1) * gain * Math.Clamp(volume, 0, 1) * (meter.Volume?.Muted() == true ? 0 : 1);
                    if (audible > best || audible == best && peak > raw) { best = audible; raw = peak; outputGain = gain; selected = meter.Pid; }
                }
                catch { /* Ignore a device/session that changed during the read. */ }
            }
            if (selected == 0) { loopback.Dispose(); return new MeterReading(false, 0, source); }
            if (selected != meterPid) { meterPid = selected; root = ProcessLoopback.Root(selected, source); }
            if (root != target) { target = root; loopback.Dispose(); retry = default; }
            if (Environment.OSVersion.Version.Build >= 20348)
            {
                if (loopback.Process == 0 && DateTime.UtcNow >= retry)
                {
                    try { loopback.Open(target); }
                    catch { retry = DateTime.UtcNow.AddSeconds(2); }
                }
                if (loopback.Process != 0)
                {
                    try
                    {
                        // Stream/session gain is already captured; apply endpoint dB once.
                        var result = loopback.Read(outputGain); return new MeterReading(true, result.Peak, source, result.Bands, true);
                    }
                    catch { loopback.Dispose(); retry = DateTime.UtcNow.AddSeconds(2); return new MeterReading(false, 0, source); }
                }
            }
            return new MeterReading(true, Math.Max(0, best), source);
        }
        catch { Clear(); refresh = DateTime.UtcNow.AddSeconds(2); return new MeterReading(false, 0, requestedSource); }
    }
    private void ClearMeters()
    {
        foreach (var meter in meters) { meter.Meter.Dispose(); meter.Volume?.Dispose(); }
        meters.Clear(); foreach (var device in devices) device.Dispose(); devices.Clear(); meterPid = 0;
    }
    private void Clear() { ClearMeters(); loopback.Dispose(); }
    public void Dispose() { Clear(); enumerator.Dispose(); }
}
