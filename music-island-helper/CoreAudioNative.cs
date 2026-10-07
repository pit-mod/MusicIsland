using System.Runtime.InteropServices;

// Read-only Core Audio ABI. Slots/IIDs follow mmdeviceapi.h, audiopolicy.h and endpointvolume.h.
// Explicit IUnknown ownership avoids runtime-generated COM wrappers.
internal sealed unsafe class AudioCom : IDisposable
{
    internal nint Pointer { get; private set; }
    internal AudioCom(nint pointer) => Pointer = pointer != 0 ? pointer : throw new COMException("Missing audio interface");
    private nint Slot(int index) => (*(nint**)Pointer)[index];
    internal AudioCom? Query(Guid iid)
    {
        nint result = 0;
        int hr = ((delegate* unmanaged[Stdcall]<nint, Guid*, nint*, int>)Slot(0))(Pointer, &iid, &result);
        return hr >= 0 && result != 0 ? new AudioCom(result) : null;
    }
    internal AudioCom ActiveRenderDevices()
    {
        nint result = 0;
        // IMMDeviceEnumerator::EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE).
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int, uint, nint*, int>)Slot(3))(Pointer, 0, 1, &result));
        return new AudioCom(result);
    }
    internal int Count()
    {
        uint count = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint*, int>)Slot(3))(Pointer, &count));
        return checked((int)count);
    }
    internal AudioCom Item(int index)
    {
        nint result = 0;
        // IMMDeviceCollection::Item and IAudioSessionEnumerator::GetSession share this ABI.
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint, nint*, int>)Slot(4))(Pointer, (uint)index, &result));
        return new AudioCom(result);
    }
    internal AudioCom SessionManager()
    {
        return Activate(new Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"));
    }
    internal AudioCom Activate(Guid iid)
    {
        nint result = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, Guid*, uint, nint, nint*, int>)Slot(3))(Pointer, &iid, 23, 0, &result));
        return new AudioCom(result);
    }
    internal AudioCom Sessions()
    {
        nint result = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, nint*, int>)Slot(5))(Pointer, &result));
        return new AudioCom(result);
    }
    internal int ProcessId()
    {
        using var control = Query(new Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"));
        if (control == null) throw new COMException("Missing session control");
        uint pid = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, uint*, int>)control.Slot(14))(control.Pointer, &pid));
        return checked((int)pid);
    }
    internal float Peak()
    {
        float value = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, float*, int>)Slot(3))(Pointer, &value));
        return value;
    }
    internal bool Muted()
    {
        int muted = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int*, int>)Slot(6))(Pointer, &muted));
        return muted != 0;
    }
    internal float SessionVolume() => FloatValue(3);
    internal float EndpointDecibels() => FloatValue(8);
    private float FloatValue(int slot)
    {
        float value = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, float*, int>)Slot(slot))(Pointer, &value));
        return value;
    }
    internal bool EndpointMuted()
    {
        int muted = 0;
        Marshal.ThrowExceptionForHR(((delegate* unmanaged[Stdcall]<nint, int*, int>)Slot(15))(Pointer, &muted));
        return muted != 0;
    }
    public void Dispose()
    {
        nint pointer = Pointer;
        if (pointer == 0) return;
        Pointer = 0;
        ((delegate* unmanaged[Stdcall]<nint, uint>)(*(nint**)pointer)[2])(pointer);
    }
}

internal sealed unsafe class AudioEnumerator : IDisposable
{
    internal readonly AudioCom DeviceEnumerator;
    private bool initialized;
    internal AudioEnumerator()
    {
        Marshal.ThrowExceptionForHR(CoInitializeEx(0, 0)); // Balance initialization on the same MTA thread.
        initialized = true;
        try
        {
            Guid clsid = new("BCDE0395-E52F-467C-8E3D-C4579291692E");
            Guid iid = new("A95664D2-9614-4F35-A746-DE8DB63617E6");
            nint result = 0;
            Marshal.ThrowExceptionForHR(CoCreateInstance(&clsid, 0, 23, &iid, &result));
            DeviceEnumerator = new AudioCom(result);
        }
        catch { CoUninitialize(); initialized = false; throw; }
    }
    public void Dispose()
    {
        if (!initialized) return;
        DeviceEnumerator.Dispose(); CoUninitialize(); initialized = false;
    }
    [DllImport("ole32.dll", ExactSpelling = true)] private static extern int CoInitializeEx(nint reserved, uint flags);
    [DllImport("ole32.dll", ExactSpelling = true)] private static extern void CoUninitialize();
    [DllImport("ole32.dll", ExactSpelling = true)] private static extern int CoCreateInstance(Guid* clsid, nint outer, uint context, Guid* iid, nint* result);
}
