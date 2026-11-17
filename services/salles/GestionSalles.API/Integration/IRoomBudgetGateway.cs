using GestionSalles.API.Models;
using GestionSalles.API.Services;
namespace GestionSalles.API.Integration;

public interface IRoomBudgetGateway
{
    Task ValidateUser(string authorization);
    Task ValidateEvent(long eventId, string authorization);
    Task Approve(ReservationSalle reservation, RoomChange proposal, string authorization);
}

public class RemoteServiceFailure(int status, string message, bool definitive = true) : Exception(message)
{
    public int Status { get; } = status;
    public bool Definitive { get; } = definitive;
}
