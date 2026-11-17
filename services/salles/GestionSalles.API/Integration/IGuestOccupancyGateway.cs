namespace GestionSalles.API.Integration;

public interface IGuestOccupancyGateway
{
    Task<long> OccupiedSeats(long eventId, string authorization);
}

public class UnavailableGuestOccupancyGateway : IGuestOccupancyGateway
{
    public Task<long> OccupiedSeats(long eventId, string authorization) =>
        throw new RemoteServiceFailure(503,"Le service Invités est indisponible : la capacité ne peut pas être réduite sans vérifier les invités.");
}
