using System.Runtime.InteropServices;
using System.Text;

// Resolve the selected GSMTC application to its Core Audio process. Transport controls
// still use the exact GSMTC session/track; audio is never taken from the global mixer.
internal static class AudioSourceIdentity
{
    internal static bool Matches(string source, string processName, string appId)
    {
        if (string.IsNullOrWhiteSpace(source) || string.IsNullOrWhiteSpace(processName)) return false;
        if (!string.IsNullOrEmpty(appId) && source.Equals(appId, StringComparison.OrdinalIgnoreCase)) return true;
        if (source.Contains('!')) return false;
        string name = Path.GetFileNameWithoutExtension(source);
        if (name.Equals(processName, StringComparison.OrdinalIgnoreCase)) return true;
        // Desktop browsers can publish an AUMID with a profile suffix instead of .exe.
        foreach (var names in new[] {
            new[] { "chrome", "Chrome", "Google.Chrome" },
            new[] { "chromium", "Chromium" },
            new[] { "msedge", "msedge", "MicrosoftEdge", "Microsoft.Edge" },
            new[] { "firefox", "Firefox", "Mozilla.Firefox" },
            new[] { "brave", "Brave", "BraveBrowser", "BraveSoftware.BraveBrowser" },
            new[] { "opera", "Opera", "OperaGX", "Opera.OperaGX" },
            new[] { "vlc", "VLC", "org.videolan.vlc" },
            new[] { "spotify", "Spotify" },
            new[] { "deezer", "Deezer", "com.deezer.deezer-desktop" }
        }) {
            if (!processName.Equals(names[0], StringComparison.OrdinalIgnoreCase)) continue;
            foreach (string alias in names.Skip(1))
                if (source.Equals(alias, StringComparison.OrdinalIgnoreCase)
                    || source.StartsWith(alias + ".", StringComparison.OrdinalIgnoreCase)
                    || source.EndsWith("." + alias, StringComparison.OrdinalIgnoreCase)) return true;
        }
        return false;
    }
    internal static string ApplicationId(int pid)
    {
        IntPtr handle = OpenProcess(0x1000, false, pid); // QUERY_LIMITED_INFORMATION only.
        if (handle == IntPtr.Zero) return "";
        try {
            uint length = 0;
            if (GetApplicationUserModelId(handle, ref length, null) != 122 || length == 0 || length > 1024) return "";
            var value = new StringBuilder((int)length);
            return GetApplicationUserModelId(handle, ref length, value) == 0 ? value.ToString() : "";
        } finally { CloseHandle(handle); }
    }
    [DllImport("kernel32.dll")] private static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
    [DllImport("kernel32.dll")] private static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
    private static extern int GetApplicationUserModelId(IntPtr handle, ref uint length, StringBuilder? value);
}
