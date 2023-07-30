public class HubHttpEvent
{
    public int DeviceId { get; set; }
    public string? DeviceName { get; set; }
    public string? Event { get; set; }
    public string? Value { get; set; }
    public string? Unit { get; set; }
    public long Timestamp { get; set; }
    public string? Hub { get; set; }
    public bool Polled { get; set; }
    public string? Extra { get; set; }

    public HubEvent ToHubEvent()
    {
        return new HubEvent
        {
            DeviceId = DeviceId,
            DeviceName = DeviceName,
            Event = Event,
            Value = Value,
            Unit = Unit,
            Recorded = DateTimeOffset.FromUnixTimeMilliseconds(Timestamp),
            Hub = Hub,
            Polled = Polled,
            Extra = Extra,
        };
    }
}
