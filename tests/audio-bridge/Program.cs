using System.Runtime.InteropServices;
using System.Text.Json;

internal static unsafe class AudioBridgeTests
{
    private static int checks;
    private static void Require(bool passed, string reason) { checks++; if (!passed) throw new InvalidOperationException(reason); }
    private static AudioSpectrum Tone(double frequency, float amplitude, bool opposite = false)
    {
        var spectrum = new AudioSpectrum();
        for (int frame = 0; frame < 15600; frame++)
        {
            if (frame == 14400) spectrum.BeginBlock();
            float value = amplitude * (float)Math.Sin(frame * 2 * Math.PI * frequency / 48000);
            spectrum.Push(value, opposite ? -value : value);
        }
        return spectrum;
    }
    private static void SpectrumChecks()
    {
        double[] frequencies = [75, 220, 600, 1700, 5000, 12000]; int[] positions = [2, 3, 1, 4, 0, 5];
        for (int band = 0; band < 6; band++)
        {
            var analyzer = Tone(frequencies[band], .2f); float[] energy = analyzer.Amplitudes(), display = analyzer.Display(1), quiet = analyzer.Display(.1f);
            Require(energy[band] > .15f, "Frequency amplitude too low");
            for (int other = 0; other < 6; other++) if (other != band) Require(energy[band] > energy[other] * 2, "Frequency bands not isolated");
            Require(display[positions[band]] > .7f, "Wrong visible frequency stroke");
            Require(quiet[positions[band]] < display[positions[band]] - .15f, "Volume attenuation lost");
            foreach (float value in analyzer.Display(0)) Require(value == 0, "Mute left active bars");
            float[] opposite = Tone(frequencies[band], .2f, true).Amplitudes();
            for (int i = 0; i < 6; i++) Require(Math.Abs(opposite[i] - energy[i]) < .000001f, "Stereo phase cancelled instruments");
        }
        var beats = new AudioSpectrum(); float animatedLow = 1, animatedHigh = 0, plainLow = 1, plainHigh = 0;
        for (int block = 0; block < 100; block++)
        {
            beats.BeginBlock();
            for (int sample = 0; sample < 1200; sample++)
            {
                double time = (block * 1200 + sample) / 48000.0;
                float amplitude = (float)(.024 + .018 * Math.Sin(time * 2 * Math.PI * 2));
                float value = amplitude * (float)Math.Sin(time * 2 * Math.PI * 75); beats.Push(value, value);
            }
            if (block >= 40)
            {
                float animated = beats.Display(.108655f)[2], plain = AudioSpectrum.Level(beats.Amplitudes()[0] * .108655f * 4);
                animatedLow = Math.Min(animatedLow, animated); animatedHigh = Math.Max(animatedHigh, animated);
                plainLow = Math.Min(plainLow, plain); plainHigh = Math.Max(plainHigh, plain);
            }
        }
        float excursion = animatedHigh - animatedLow;
        Require(excursion > (plainHigh - plainLow) * 1.35f, "Quiet beats did not gain movement");
        // Recorded C++ fixture values keep the two implementations in sync.
        Require(Math.Abs(excursion - .638827f) < .00002f, "C# beat response differs from native C++");
        float listening = Tone(75, .05f).Display(AudioSpectrum.Gain(-19.279f))[2];
        Require(listening > .55f && listening < .85f, "Normal listening levels too small or saturated");
        foreach (float value in Tone(75, .00001f).Display(.108655f)) Require(value == 0, "Inaudible noise amplified");
        var silence = new AudioSpectrum(); silence.Push(float.NaN, float.PositiveInfinity);
        foreach (float value in silence.Display(1)) Require(value == 0 && float.IsFinite(value), "Invalid PCM invented activity");
        var reset = Tone(600, .2f); reset.Reset(); foreach (float value in reset.Display(1)) Require(value == 0, "Reset retained old energy");
        string json = JsonSerializer.Serialize(new MeterReading(true, .02f, "test", [.1f, .2f, .3f, .4f, .5f, .6f], true), BridgeJsonContext.Default.MeterReading);
        using var document = JsonDocument.Parse(json);
        Require(document.RootElement.GetProperty("spectrum").GetBoolean() && document.RootElement.GetProperty("bands").GetArrayLength() == 6, "AOT protocol lost frequency bands");
        Console.WriteLine($"C++/C# quiet-beat excursion: {excursion:F6}");
    }
    private static nint Slot(nint value, int index) => (*(nint**)value)[index];
    [StructLayout(LayoutKind.Sequential, Pack = 2)]
    private struct Format { internal ushort Tag, Channels; internal uint Rate, Bytes; internal ushort Align, Bits, Extra; }
    private static void NativeChecks()
    {
        Console.WriteLine("Native: enumerate endpoint");
        using var enumerator = new AudioEnumerator(); nint value = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int, int, nint*, int>)Slot(enumerator.DeviceEnumerator.Pointer, 4))(enumerator.DeviceEnumerator.Pointer, 0, 1, &value));
        using var endpoint = new AudioCom(value);
        Console.WriteLine("Native: activate private render stream");
        using var client = endpoint.Activate(new Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"));
        Format format = new() { Tag = 1, Channels = 2, Rate = 48000, Bits = 16, Align = 4, Bytes = 192000, Extra = 0 }; Guid session = Guid.NewGuid();
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int, uint, long, long, Format*, Guid*, int>)Slot(client.Pointer, 3))(client.Pointer, 0, 0x80000000, 2000000, 0, &format, &session));
        Guid renderId = new("F294ACFC-3146-4483-A7BF-ADDCA7C260E2"); value = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, Guid*, nint*, int>)Slot(client.Pointer, 14))(client.Pointer, &renderId, &value));
        using var output = new AudioCom(value);
        Guid volumeId = new("87CE5498-68D6-44E5-9215-6DA47EF883D8"); value = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, Guid*, nint*, int>)Slot(client.Pointer, 14))(client.Pointer, &volumeId, &value));
        using var volume = new AudioCom(value);
        Console.WriteLine("Native: silence private session and prime PCM");
        // This test owns a private zero-volume session; no audible tones and no changes to other players.
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, float, Guid*, int>)Slot(volume.Pointer, 4))(volume.Pointer, 0, null));
        uint nativeCapacity = 0; Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint*, int>)Slot(client.Pointer, 4))(client.Pointer, &nativeCapacity));
        uint capacity = nativeCapacity;
        ulong phase = 0;
        void Feed()
        {
            uint padding = 0; Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint*, int>)Slot(client.Pointer, 6))(client.Pointer, &padding));
            uint frames = capacity - padding; if (frames == 0) return; nint data = 0;
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint, nint*, int>)Slot(output.Pointer, 3))(output.Pointer, frames, &data));
            var pcm = (short*)data;
            for (int i = 0; i < frames; i++, phase++) { short sample = (short)(8192 * Math.Sin(phase * 2 * Math.PI * 440 / 48000)); pcm[i * 2] = pcm[i * 2 + 1] = sample; }
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint, uint, int>)Slot(output.Pointer, 4))(output.Pointer, frames, 0));
        }
        Feed(); Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int>)Slot(client.Pointer, 10))(client.Pointer));
        Console.WriteLine("Native: start loopback meter");
        try
        {
            using var meter = new PlayerAudioMeter(); string source = System.Diagnostics.Process.GetCurrentProcess().ProcessName;
            MeterReading reading = new(false, 0, source);
            using var sessions = endpoint.SessionManager(); using var allSessions = sessions.Sessions(); bool nonzero = false;
            var rawMeters = new List<AudioCom>();
            try
            {
                for (int i = 0; i < allSessions.Count(); i++)
                {
                    using var item = allSessions.Item(i); if (item.ProcessId() != Environment.ProcessId) continue;
                    var peak = item.Query(new Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064")); if (peak != null) rawMeters.Add(peak);
                }
                // Observe the render meter throughout playback: a single late
                // sample can land on an underrun while build jobs occupy the CPU.
                for (int i = 0; i < 45; i++)
                {
                    Feed(); Thread.Sleep(25);
                    foreach (var peak in rawMeters) nonzero |= peak.Peak() > .24f;
                    reading = meter.Read(source, true);
                    if (i == 0) Console.WriteLine("Native: first meter read complete");
                }
            }
            finally { foreach (var peak in rawMeters) peak.Dispose(); }
            Require(reading.available && reading.spectrum && reading.bands?.Length == 6, "Native process loopback/COM activation did not produce spectral readings");
            Require(reading.peak == 0 && reading.bands!.All(level => level == 0), "Zero player volume or other-player isolation failed");
            Require(nonzero, "Native test must have nonzero PCM before stream attenuation");
            Require(meter.Read(source, false).peak == 0, "Pause did not clear capture");
            for (int i = 0; i < 10; i++) { Feed(); Thread.Sleep(25); reading = meter.Read(source, true); }
            Require(reading.spectrum && reading.peak == 0, "Capture did not reopen cleanly");
            Require(!meter.Read("missing-player-for-test", true).available, "Source replacement leaked old audio");
        }
        finally { ((delegate* unmanaged[Stdcall]<nint, int>)Slot(client.Pointer, 11))(client.Pointer); }
    }
    private static int Main()
    {
        try { SpectrumChecks(); NativeChecks(); Console.WriteLine($"PASS: {checks} spectral parity, volume/mute, protocol, native capture and zero-volume isolation checks"); return 0; }
        catch (Exception error) { Console.Error.WriteLine(error); return 1; }
    }
}
