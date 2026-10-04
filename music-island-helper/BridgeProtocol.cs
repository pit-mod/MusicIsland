using System.Text.Json.Serialization;

// Explicit protocol types keep every existing field while allowing reflection-free publishing.
internal sealed record BridgeSuccess(long id, bool ok, object data);
internal sealed record BridgeFailure(long id, bool ok, string error, string stage, string code);
internal sealed record SessionSummary(string session, string source, string status);
internal sealed record CommandResult(bool accepted, string reason);
internal sealed record UnavailableData(bool available = false);
internal sealed record MeterReading(bool available, float peak, string source);
internal sealed record PollData
{
    public bool available { get; init; }
    public string session { get; init; } = "";
    public string track { get; init; } = "";
    public string source { get; init; } = "";
    public string title { get; init; } = "";
    public string artist { get; init; } = "";
    public string album { get; init; } = "";
    public bool playing { get; init; }
    public double position { get; init; }
    public double duration { get; init; }
    public double rate { get; init; }
    public long stamp { get; init; }
    public long timelineStamp { get; init; }
    public string artHash { get; init; } = "";
    public string art { get; init; } = "";
    public bool play { get; init; }
    public bool pause { get; init; }
    public bool previous { get; init; }
    public bool next { get; init; }
    public bool seek { get; init; }
    public bool open { get; init; }
}

[JsonSourceGenerationOptions(GenerationMode = JsonSourceGenerationMode.Metadata)]
[JsonSerializable(typeof(BridgeSuccess))]
[JsonSerializable(typeof(BridgeFailure))]
[JsonSerializable(typeof(SessionSummary[]))]
[JsonSerializable(typeof(CommandResult))]
[JsonSerializable(typeof(UnavailableData))]
[JsonSerializable(typeof(MeterReading))]
[JsonSerializable(typeof(PollData))]
internal partial class BridgeJsonContext : JsonSerializerContext { }
