using System.Security.Cryptography;

// Browsers can announce the new title before updating the thumbnail. Keep artwork
// scoped to the track and recheck it frequently, including after an initial success.
internal sealed class MediaArtworkCache
{
    internal string Hash { get; private set; } = "";
    internal string Data { get; private set; } = "";
    private string track = "", previousHash = "";
    private long changedAt, nextRead = long.MinValue;
    internal void ObserveTrack(string value, long now)
    {
        if (track == value) return;
        track = value; previousHash = Hash; Hash = Data = "";
        changedAt = now; nextRead = long.MinValue;
    }
    internal bool ShouldRead(long now) => now >= nextRead;
    internal void Reading(long now)
    {
        nextRead = now + (now - changedAt < 15000 ? 400 : Hash.Length == 0 ? 1000 : 2000);
    }
    internal void Accept(byte[] bytes, long now)
    {
        string hash = Convert.ToHexString(SHA256.HashData(bytes));
        // Briefly withhold the previous song's thumbnail. Shared album covers are
        // accepted after the grace period, so a genuine shared image never stays hidden.
        if (hash == previousHash && now - changedAt < 1500) return;
        Hash = hash; Data = Convert.ToBase64String(bytes);
    }
}
