using GestionSalles.API.Integration;
using GestionSalles.API.Models;
using GestionSalles.API.Services;
namespace GestionSalles.Tests.Infrastructure;

public class ApprovingBudgetGateway : IRoomBudgetGateway
{
    public Func<ReservationSalle, RoomChange, Task>? OnApprove { get; set; }
    public List<(long Id,long Version,decimal Price)> Calls { get; } = new();
    public Task ValidateUser(string authorization) => Task.CompletedTask;
    public Task ValidateEvent(long id,string authorization) => Task.CompletedTask;
    public async Task Approve(ReservationSalle reservation,RoomChange proposal,string authorization)
    {
        Calls.Add((reservation.Id,reservation.BudgetVersion,proposal.Prix));
        if (OnApprove is not null) await OnApprove(reservation,proposal);
    }
}
