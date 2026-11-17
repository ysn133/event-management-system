using GestionSalles.API.Integration;
namespace GestionSalles.Tests.Infrastructure;

public class FakeGuestOccupancyGateway : IGuestOccupancyGateway
{
    public Dictionary<long,long> Counts { get; } = new();
    public List<long> Calls { get; } = new();
    public Func<long,Task<long>>? OnCount { get; set; }
    public Task<long> OccupiedSeats(long eventId,string authorization)
    {
        Calls.Add(eventId);
        return OnCount?.Invoke(eventId) ?? Task.FromResult(Counts.GetValueOrDefault(eventId));
    }
}
