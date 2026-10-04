using System.Diagnostics;

// The same per-application peak meters as before, without NAudio or dynamic COM marshalling.
internal sealed class PlayerAudioMeter : IDisposable
{
    private readonly AudioEnumerator enumerator = new();
    private readonly List<AudioCom> devices = new();
    private readonly List<(AudioCom Meter, AudioCom? Volume)> meters = new();
    private string source = "";
    private DateTime refresh;
    internal MeterReading Read(string requestedSource, bool playing)
    {
        try
        {
            if (source != requestedSource || DateTime.UtcNow >= refresh)
            {
                Clear(); source = requestedSource; refresh = DateTime.UtcNow.AddSeconds(2);
                var identities = new Dictionary<int, (string Name, string AppId)>();
                using var endpoints = enumerator.DeviceEnumerator.ActiveRenderDevices();
                for (int deviceIndex = 0, count = endpoints.Count(); deviceIndex < count; deviceIndex++)
                {
                    var device = endpoints.Item(deviceIndex); devices.Add(device);
                    using var manager = device.SessionManager();
                    using var sessions = manager.Sessions();
                    for (int i = 0, sessionCount = sessions.Count(); i < sessionCount; i++)
                    {
                        using var session = sessions.Item(i);
                        AudioCom? peakMeter = null;
                        AudioCom? volume = null;
                        try
                        {
                            int pid = session.ProcessId();
                            if (!identities.TryGetValue(pid, out var identity))
                            {
                                using var process = Process.GetProcessById(pid);
                                identity = (process.ProcessName, AudioSourceIdentity.ApplicationId(pid)); identities[pid] = identity;
                            }
                            if (!AudioSourceIdentity.Matches(source, identity.Name, identity.AppId)) continue;
                            peakMeter = session.Query(new Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"));
                            if (peakMeter == null) continue;
                            volume = session.Query(new Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"));
                            meters.Add((peakMeter, volume)); peakMeter = volume = null; // Transfer both owned references.
                        }
                        catch { /* A disappearing process/session must not hide other sessions. */ }
                        finally { peakMeter?.Dispose(); volume?.Dispose(); }
                    }
                }
            }
            float peak = 0;
            if (playing) foreach (var meter in meters)
                if (meter.Volume == null || !meter.Volume.Muted()) peak = Math.Max(peak, meter.Meter.Peak());
            return new MeterReading(meters.Count > 0, float.IsFinite(peak) ? Math.Clamp(peak, 0, 1) : 0, source);
        }
        catch { Clear(); refresh = DateTime.UtcNow.AddSeconds(2); return new MeterReading(false, 0f, requestedSource); }
    }
    private void Clear()
    {
        foreach (var meter in meters) { meter.Meter.Dispose(); meter.Volume?.Dispose(); }
        meters.Clear(); foreach (var device in devices) device.Dispose(); devices.Clear();
    }
    public void Dispose() { Clear(); enumerator.Dispose(); }
}
