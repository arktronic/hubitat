using System.ComponentModel.DataAnnotations.Schema;
using System.ComponentModel.DataAnnotations;

public class HubEvent
{
    [Key]
    [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
    public long? Id { get; set; }
    public int DeviceId { get; set; }
    public string? DeviceName { get; set; }
    public string? Event { get; set; }
    public string? Value { get; set; }
    public string? Unit { get; set; }
    public DateTimeOffset Recorded { get; set; }
    public string? Hub { get; set; }
    public bool Polled { get; set; }
    [Column(TypeName = "json")]
    public string? Extra { get; set; }
}
