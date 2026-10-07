using System.Diagnostics;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;

// Explicit Core Audio ABI keeps process loopback compatible with Native AOT.
// Only filter energy is retained; PCM is neither stored nor written to disk.
internal sealed unsafe class ProcessLoopback : IDisposable
{
    private AudioCom? client, capture;
    private nint packets;
    private readonly AudioSpectrum analysis = new();
    internal int Process { get; private set; }
    private static nint Slot(nint value, int index) => (*(nint**)value)[index];
    [StructLayout(LayoutKind.Sequential, Pack = 2)]
    private struct Format { internal ushort Tag, Channels; internal uint Rate, Bytes; internal ushort Align, Bits, Extra; }
    [StructLayout(LayoutKind.Sequential)]
    private struct ActivationParameters { internal int Type; internal uint Process; internal int Mode; }
    [StructLayout(LayoutKind.Explicit, Size = 24)]
    private struct Variant
    {
        [FieldOffset(0)] internal ushort Type;
        [FieldOffset(8)] internal uint Size;
        [FieldOffset(16)] internal nint Data;
    }
    private sealed class Completion : IDisposable
    {
        [StructLayout(LayoutKind.Sequential)]
        private struct Native { internal nint Vtable, Context; internal int References; }
        private static readonly nint Table = CreateTable();
        private nint pointer;
        private int lifetime;
        internal readonly nint Ready = CreateEventW(0, true, false, null);
        internal int Result = unchecked((int)0x8000000A);
        internal nint Client;
        internal nint Pointer => pointer;
        internal Completion()
        {
            if (Ready == 0) throw new System.ComponentModel.Win32Exception();
            var node = (Native*)NativeMemory.AllocZeroed((nuint)sizeof(Native));
            node->Vtable = Table; node->Context = GCHandle.ToIntPtr(GCHandle.Alloc(this)); node->References = 1;
            pointer = (nint)node;
        }
        private static nint CreateTable()
        {
            var table = (nint*)NativeMemory.Alloc((nuint)(4 * sizeof(nint)));
            table[0] = (nint)(delegate* unmanaged[Stdcall]<nint, Guid*, nint*, int>)&Query;
            table[1] = (nint)(delegate* unmanaged[Stdcall]<nint, uint>)&AddRef;
            table[2] = (nint)(delegate* unmanaged[Stdcall]<nint, uint>)&Release;
            table[3] = (nint)(delegate* unmanaged[Stdcall]<nint, nint, int>)&Completed;
            return (nint)table;
        }
        [UnmanagedCallersOnly(CallConvs = [typeof(CallConvStdcall)])]
        private static int Query(nint self, Guid* iid, nint* result)
        {
            *result = 0;
            if (*iid != new Guid("00000000-0000-0000-C000-000000000046")
                && *iid != new Guid("41D949AB-9862-444A-80F6-C261334DA5EB")
                && *iid != new Guid("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90")) return unchecked((int)0x80004002);
            Interlocked.Increment(ref ((Native*)self)->References); *result = self; return 0;
        }
        [UnmanagedCallersOnly(CallConvs = [typeof(CallConvStdcall)])]
        private static uint AddRef(nint self) => (uint)Interlocked.Increment(ref ((Native*)self)->References);
        [UnmanagedCallersOnly(CallConvs = [typeof(CallConvStdcall)])]
        private static uint Release(nint self) => Drop(self);
        private static uint Drop(nint self)
        {
            var node = (Native*)self; int remaining = Interlocked.Decrement(ref node->References);
            if (remaining == 0)
            {
                var handle = GCHandle.FromIntPtr(node->Context); var context = (Completion)handle.Target!;
                if (context.Client != 0) new AudioCom(context.Client).Dispose();
                CloseHandle(context.Ready); handle.Free(); NativeMemory.Free(node);
            }
            return (uint)remaining;
        }
        [UnmanagedCallersOnly(CallConvs = [typeof(CallConvStdcall)])]
        private static int Completed(nint self, nint operation)
        {
            var context = (Completion)GCHandle.FromIntPtr(((Native*)self)->Context).Target!;
            try
            {
                int activated = 0; nint value = 0;
                int result = ((delegate* unmanaged[Stdcall]<nint, int*, nint*, int>)Slot(operation, 3))(operation, &activated, &value);
                context.Result = result < 0 ? result : activated;
                if (value != 0)
                {
                    using var objectRef = new AudioCom(value);
                    if (context.Result >= 0)
                    {
                        using var audioClient = objectRef.Query(new Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"));
                        if (audioClient == null) context.Result = unchecked((int)0x80004002);
                        else
                        {
                            ((delegate* unmanaged[Stdcall]<nint, uint>)Slot(audioClient.Pointer, 1))(audioClient.Pointer);
                            context.Client = audioClient.Pointer;
                        }
                    }
                }
            }
            catch (Exception error) { context.Result = error.HResult; }
            // The waiting thread can finish or time out while this callback is
            // running. Keep our reference/event alive until BOTH sides finish.
            SetEvent(context.Ready);
            if ((Interlocked.Or(ref context.lifetime, 2) & 1) != 0) Drop(self);
            return 0;
        }
        internal nint TakeClient() { nint result = Client; Client = 0; return result; }
        internal void ActivationFailed() { Interlocked.Or(ref lifetime, 2); }
        public void Dispose()
        {
            nint value = Interlocked.Exchange(ref pointer, 0);
            if (value != 0 && (Interlocked.Or(ref lifetime, 1) & 2) != 0) Drop(value);
        }
    }
    internal void Open(int pid)
    {
        Dispose();
        try
        {
            using var completion = new Completion();
            ActivationParameters parameters = new() { Type = 1, Process = (uint)pid, Mode = 0 };
            Variant variant = new() { Type = 65, Size = (uint)sizeof(ActivationParameters), Data = (nint)(&parameters) };
            Guid iid = new("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"); nint operation = 0;
            int activated = ActivateAudioInterfaceAsync("VAD\\Process_Loopback", &iid, &variant, completion.Pointer, &operation);
            using var operationRef = operation == 0 ? null : new AudioCom(operation);
            if (activated < 0) completion.ActivationFailed();
            Marshal.ThrowExceptionForHR(activated);
            if (WaitForSingleObject(completion.Ready, 5000) != 0) throw new TimeoutException("Process audio activation timed out");
            Marshal.ThrowExceptionForHR(completion.Result); client = new AudioCom(completion.TakeClient());
            Format format = new() { Tag = 3, Channels = 2, Rate = 48000, Bits = 32, Align = 8, Bytes = 384000 };
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int, uint, long, long, Format*, Guid*, int>)Slot(client.Pointer, 3))(client.Pointer, 0, 0x80060000, 0, 0, &format, null));
            packets = CreateEventW(0, false, false, null); if (packets == 0) throw new System.ComponentModel.Win32Exception();
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, nint, int>)Slot(client.Pointer, 13))(client.Pointer, packets));
            Guid captureId = new("C8ADBD64-E71E-48A0-A4DE-185C395CD317"); nint value = 0;
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, Guid*, nint*, int>)Slot(client.Pointer, 14))(client.Pointer, &captureId, &value));
            capture = new AudioCom(value);
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int>)Slot(client.Pointer, 10))(client.Pointer)); Process = pid;
        }
        catch { Dispose(); throw; }
    }
    internal (float Peak, float[] Bands) Read(float outputGain)
    {
        if (capture == null) throw new InvalidOperationException("No process audio capture");
        analysis.BeginBlock(); float peak = 0; uint count = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint*, int>)Slot(capture.Pointer, 5))(capture.Pointer, &count));
        while (count != 0)
        {
            nint data = 0; uint frames = 0, flags = 0;
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, nint*, uint*, uint*, ulong*, ulong*, int>)Slot(capture.Pointer, 3))(capture.Pointer, &data, &frames, &flags, null, null));
            try
            {
                if ((flags & 1) != 0) analysis.Reset();
                var pcm = (float*)data;
                for (int i = 0; i < frames; i++)
                {
                    float left = 0, right = 0;
                    if (pcm != null && (flags & 2) == 0) { left = float.IsFinite(pcm[i * 2]) ? pcm[i * 2] : 0; right = float.IsFinite(pcm[i * 2 + 1]) ? pcm[i * 2 + 1] : 0; }
                    peak = Math.Max(peak, Math.Max(Math.Abs(left), Math.Abs(right))); analysis.Push(left, right);
                }
            }
            finally { Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint, int>)Slot(capture.Pointer, 4))(capture.Pointer, frames)); }
            Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint*, int>)Slot(capture.Pointer, 5))(capture.Pointer, &count));
        }
        if (peak == 0) analysis.Reset();
        return (peak * outputGain, analysis.Display(outputGain));
    }
    public void Dispose()
    {
        if (client != null) ((delegate* unmanaged[Stdcall]<nint, int>)Slot(client.Pointer, 11))(client.Pointer);
        capture?.Dispose(); client?.Dispose(); capture = client = null;
        if (packets != 0) CloseHandle(packets); packets = 0; Process = 0; analysis.Reset();
    }
    internal static int Root(int pid, string source)
    {
        nint snapshot = CreateToolhelp32Snapshot(2, 0); if (snapshot == -1) return pid;
        try
        {
            var parents = new Dictionary<uint, uint>(); ProcessEntry entry = new() { Size = (uint)sizeof(ProcessEntry) };
            if (Process32FirstW(snapshot, &entry)) do { parents[entry.Id] = entry.Parent; } while (Process32NextW(snapshot, &entry));
            for (int i = 0; i < 32 && parents.TryGetValue((uint)pid, out uint parent) && parent != 0 && parent != pid; i++)
            {
                try { using var process = System.Diagnostics.Process.GetProcessById((int)parent); if (!AudioSourceIdentity.Matches(source, process.ProcessName, AudioSourceIdentity.ApplicationId((int)parent))) break; pid = (int)parent; }
                catch { break; }
            }
            return pid;
        }
        finally { CloseHandle(snapshot); }
    }
    [StructLayout(LayoutKind.Sequential)]
    private struct ProcessEntry { internal uint Size, Usage, Id; internal nuint Heap; internal uint Module, Threads, Parent; internal int Priority; internal uint Flags; internal fixed char Name[260]; }
    [DllImport("Mmdevapi.dll", CharSet = CharSet.Unicode, ExactSpelling = true)] private static extern int ActivateAudioInterfaceAsync(string path, Guid* iid, Variant* parameters, nint completion, nint* operation);
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, ExactSpelling = true)] private static extern nint CreateEventW(nint attributes, bool manualReset, bool initial, string? name);
    [DllImport("kernel32.dll", ExactSpelling = true)] private static extern bool SetEvent(nint handle);
    [DllImport("kernel32.dll", ExactSpelling = true)] private static extern uint WaitForSingleObject(nint handle, uint milliseconds);
    [DllImport("kernel32.dll", ExactSpelling = true)] private static extern bool CloseHandle(nint handle);
    [DllImport("kernel32.dll", ExactSpelling = true)] private static extern nint CreateToolhelp32Snapshot(uint flags, uint process);
    [DllImport("kernel32.dll", ExactSpelling = true)] private static extern bool Process32FirstW(nint snapshot, ProcessEntry* entry);
    [DllImport("kernel32.dll", ExactSpelling = true)] private static extern bool Process32NextW(nint snapshot, ProcessEntry* entry);
}
