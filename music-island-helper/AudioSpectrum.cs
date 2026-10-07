// Same six stereo bands, 10 ms analysis windows and beat contrast as the native island.
internal sealed class AudioSpectrum
{
    private sealed class Filter
    {
        private readonly double b0, b1, b2, a1, a2;
        private double z1, z2;
        internal Filter(double frequency, bool highpass)
        {
            double w = 2 * Math.PI * frequency / 48000, cosine = Math.Cos(w), alpha = Math.Sin(w) / Math.Sqrt(2), divisor = 1 + alpha;
            b0 = (highpass ? 1 + cosine : 1 - cosine) / (2 * divisor);
            b1 = (highpass ? -(1 + cosine) : 1 - cosine) / divisor; b2 = b0;
            a1 = -2 * cosine / divisor; a2 = (1 - alpha) / divisor;
        }
        internal double Push(double value)
        {
            double output = b0 * value + z1; z1 = b1 * value - a1 * output + z2; z2 = b2 * value - a2 * output;
            if (Math.Abs(z1) < 1e-20) z1 = 0; if (Math.Abs(z2) < 1e-20) z2 = 0;
            return output;
        }
        internal void Reset() { z1 = z2 = 0; }
    }
    private sealed class Band
    {
        internal readonly Filter[] High, Low;
        internal double Energy, Peak;
        internal Band(double low, double high)
        {
            High = [new(low, true), new(low, true)]; Low = [new(high, false), new(high, false)];
        }
    }
    private readonly Band[] bands;
    private readonly float[] latest = new float[6], trend = new float[6];
    private int frames;
    private bool hasWindow, trendReady;
    internal AudioSpectrum()
    {
        double[] edges = [20, 130, 350, 1000, 3000, 8000, 20000];
        bands = Enumerable.Range(0, 6).Select(i => new Band(edges[i], edges[i + 1])).ToArray();
    }
    private void ClearWindow() { frames = 0; foreach (var band in bands) band.Energy = band.Peak = 0; }
    internal void BeginBlock() { ClearWindow(); Array.Clear(latest); hasWindow = false; }
    internal void Reset()
    {
        BeginBlock(); Array.Clear(trend); trendReady = false;
        foreach (var band in bands) for (int c = 0; c < 2; c++) { band.High[c].Reset(); band.Low[c].Reset(); }
    }
    internal void Push(float left, float right)
    {
        left = float.IsFinite(left) ? Math.Clamp(left, -1, 1) : 0; right = float.IsFinite(right) ? Math.Clamp(right, -1, 1) : 0;
        foreach (var band in bands) for (int c = 0; c < 2; c++)
        {
            double value = band.Low[c].Push(band.High[c].Push(c == 0 ? left : right));
            band.Energy += value * value; band.Peak = Math.Max(band.Peak, Math.Abs(value));
        }
        if (++frames == 480)
        {
            FillAmplitudes(latest); hasWindow = true;
            if (!trendReady) { latest.CopyTo(trend, 0); trendReady = true; }
            else for (int i = 0; i < 6; i++) trend[i] += (latest[i] - trend[i]) * (float)(1 - Math.Exp(-.01 * 6));
            ClearWindow();
        }
    }
    private void FillAmplitudes(float[] result)
    {
        for (int i = 0; i < 6; i++) result[i] = frames == 0 ? 0 : (float)(.7 * Math.Sqrt(bands[i].Energy / frames) + .3 * bands[i].Peak);
    }
    internal float[] Amplitudes()
    {
        if (hasWindow) return (float[])latest.Clone();
        float[] result = new float[6]; FillAmplitudes(result); return result;
    }
    internal float[] Display(float outputGain)
    {
        float[] values = Amplitudes(), result = new float[6];
        int[] order = [4, 2, 0, 1, 3, 5]; float[] sensitivity = [1, 1.1f, 1.25f, 1.4f, 1.6f, 1.8f];
        for (int i = 0; i < 6; i++)
        {
            int band = order[i]; float amplitude = Math.Max(0, values[band] + (trendReady ? 1.5f * (values[band] - trend[band]) : 0));
            result[i] = Level(amplitude * outputGain * 4 * sensitivity[band]);
        }
        return result;
    }
    internal static float Gain(float decibels, bool muted = false) => muted || !float.IsFinite(decibels) ? 0 : MathF.Pow(10, Math.Clamp(decibels, -160, 24) / 20);
    internal static float Level(float peak)
    {
        if (!float.IsFinite(peak) || peak <= 0) return 0;
        float level = MathF.Pow(Math.Clamp((20 * MathF.Log10(peak) + 66) / 66, 0, 1), .85f);
        return level + .5f * level * (1 - level) * (1 - level);
    }
}
