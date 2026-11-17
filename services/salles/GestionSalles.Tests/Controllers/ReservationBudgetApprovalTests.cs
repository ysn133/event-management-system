using GestionSalles.API.Controllers.API;
using GestionSalles.API.DTOs;
using GestionSalles.API.Models;
using GestionSalles.API.Integration;
using GestionSalles.Tests.Infrastructure;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.Tests.Controllers;
public class ReservationBudgetApprovalTests
{
    private static ReservationCreateDto Request(long room) => new() { SalleId=room,EvenementId=1,
        DateDebut=new DateTime(2027,1,10,9,0,0,DateTimeKind.Utc),DateFin=new DateTime(2027,1,10,18,0,0,DateTimeKind.Utc),Prix=700 };
    private static async Task<Salle> Room(TestDatabase db)
    {
        var room=new Salle { Nom="Budget room",Capacite=50,Adresse="Agadir",Prix=700 };
        db.Context.Salles.Add(room); await db.Context.SaveChangesAsync(); return room;
    }
    [Fact]
    public async Task ApprovedReservationIsKeptAfterCancellation()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var controller=new ReservationsApiController(db.Context,new ApprovingBudgetGateway(),new FakeGuestOccupancyGateway());
        await controller.Create(Request(room.Id)); var reservation=await db.Context.ReservationsSalles.SingleAsync();
        await controller.Cancel(reservation.Id);
        Assert.IsType<ConflictObjectResult>(await controller.Delete(reservation.Id));
        Assert.Single(await db.Context.ReservationsSalles.ToListAsync());
    }
    [Fact]
    public async Task DefinitivelyRejectedProposalCanBeCancelledAndRemoved()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway { OnApprove=(_,_)=>throw new RemoteServiceFailure(409,"Refus") };
        var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway());
        await controller.Create(Request(room.Id)); var reservation=await db.Context.ReservationsSalles.SingleAsync();
        Assert.IsType<OkObjectResult>(await controller.Cancel(reservation.Id));
        Assert.IsType<OkObjectResult>(await controller.Delete(reservation.Id));
        Assert.Empty(await db.Context.ReservationsSalles.ToListAsync());
    }
    [Theory]
    [InlineData(404)] [InlineData(409)]
    public async Task BudgetRefusalDoesNotValidateReservation(int status)
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway { OnApprove=(_,_)=>throw new RemoteServiceFailure(status,"Budget refusé") };
        var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway());
        var result=await controller.Create(Request(room.Id));
        Assert.Equal(status,Assert.IsAssignableFrom<ObjectResult>(result.Result).StatusCode);
        var proposal=await db.Context.ReservationsSalles.SingleAsync();
        Assert.False(proposal.BudgetApproved); Assert.Equal("BLOCKED",proposal.BudgetSyncState);
    }
    [Fact]
    public async Task CreationAndReplayUseOneReservationAndOneApproval()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway(); var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway());
        var dto=Request(room.Id);
        Assert.IsType<CreatedAtActionResult>((await controller.Create(dto)).Result);
        Assert.IsType<CreatedAtActionResult>((await controller.Create(dto)).Result);
        Assert.Single(await db.Context.ReservationsSalles.ToListAsync()); Assert.Single(gateway.Calls);
        Assert.True((await db.Context.ReservationsSalles.SingleAsync()).BudgetApproved);
    }
    [Fact]
    public async Task TimeoutLeavesRetryableProposalWithTheSameVersion()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        bool unavailable=true;
        var gateway=new ApprovingBudgetGateway { OnApprove=(_,_)=> unavailable ? throw new RemoteServiceFailure(503,"Timeout",false) : Task.CompletedTask };
        var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway());
        Assert.Equal(503,Assert.IsType<ObjectResult>((await controller.Create(Request(room.Id))).Result).StatusCode);
        var reservation=await db.Context.ReservationsSalles.SingleAsync();
        Assert.Equal("PENDING",reservation.BudgetSyncState); Assert.False(reservation.BudgetApproved);
        unavailable=false;
        Assert.IsType<OkObjectResult>(await controller.SynchronizeBudget(reservation.Id));
        Assert.Equal(new long[]{1,1},gateway.Calls.Select(c=>c.Version));
        Assert.Single(await db.Context.ReservationsSalles.ToListAsync());
    }
    [Fact]
    public async Task RefusedPriceChangePreservesPreviouslyApprovedPrice()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway(); var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway());
        var dto=Request(room.Id); await controller.Create(dto);
        var reservation=await db.Context.ReservationsSalles.SingleAsync();
        gateway.OnApprove=(_,_)=>throw new RemoteServiceFailure(409,"Solde insuffisant");
        var result=await controller.Update(reservation.Id,new ReservationUpdateDto { DateDebut=dto.DateDebut,DateFin=dto.DateFin,Prix=1200,Version=1 });
        Assert.IsType<ConflictObjectResult>(result);
        Assert.Equal(700,reservation.Prix); Assert.True(reservation.BudgetApproved); Assert.Equal("BLOCKED",reservation.BudgetSyncState);
        gateway.OnApprove=null;
        Assert.IsType<OkObjectResult>(await controller.Update(reservation.Id,new ReservationUpdateDto { DateDebut=dto.DateDebut,DateFin=dto.DateFin,Prix=800,Version=2 }));
        Assert.Equal(800,reservation.Prix); Assert.Equal(3,reservation.BudgetVersion);
    }
    [Fact]
    public async Task PendingUpdateMustBeResolvedBeforeAnotherChange()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway { OnApprove=(_,_)=>throw new RemoteServiceFailure(503,"Timeout",false) };
        var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway()); var dto=Request(room.Id);
        await controller.Create(dto); var reservation=await db.Context.ReservationsSalles.SingleAsync();
        Assert.IsType<ConflictObjectResult>(await controller.Update(reservation.Id,new ReservationUpdateDto { DateDebut=dto.DateDebut,DateFin=dto.DateFin,Prix=800 }));
        Assert.Single(gateway.Calls);
    }
    [Fact]
    public async Task RefusedNewProposalDoesNotKeepRoomUnavailable()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway { OnApprove=(_,_)=>throw new RemoteServiceFailure(409,"Budget refusé") };
        var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway()); await controller.Create(Request(room.Id));
        gateway.OnApprove=null;
        Assert.IsType<CreatedAtActionResult>((await controller.Create(Request(room.Id))).Result);
    }
    [Fact]
    public async Task CancellingManagedReservationSynchronizesBeforeMarkingCancelled()
    {
        await using var db=new TestDatabase(); var room=await Room(db);
        var gateway=new ApprovingBudgetGateway(); var controller=new ReservationsApiController(db.Context,gateway,new FakeGuestOccupancyGateway());
        await controller.Create(Request(room.Id)); var reservation=await db.Context.ReservationsSalles.SingleAsync();
        Assert.IsType<OkObjectResult>(await controller.Cancel(reservation.Id));
        Assert.Equal("ANNULEE",reservation.Statut); Assert.Equal(2,reservation.BudgetVersion); Assert.Equal(2,gateway.Calls.Count);
    }
}
