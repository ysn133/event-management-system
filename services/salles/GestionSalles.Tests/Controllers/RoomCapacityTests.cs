using GestionSalles.API.Controllers.API;
using GestionSalles.API.DTOs;
using GestionSalles.API.Integration;
using GestionSalles.API.Models;
using GestionSalles.Tests.Infrastructure;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.Tests.Controllers;

public class RoomCapacityTests
{
    private static async Task<Salle> Room(TestDatabase db,int capacity=100)
    {
        var room=new Salle {Nom="Capacity room",Adresse="Agadir",Capacite=capacity,Prix=700};
        db.Context.Salles.Add(room); await db.Context.SaveChangesAsync(); return room;
    }
    private static async Task<ReservationSalle> Booking(TestDatabase db,Salle room,long eventId=1,bool approved=true,string status="CONFIRMEE")
    {
        var booking=new ReservationSalle {Salle=room,SalleId=room.Id,EvenementId=eventId,
            DateDebut=new DateTime(2082,1,10,9,0,0,DateTimeKind.Utc),DateFin=new DateTime(2082,1,10,18,0,0,DateTimeKind.Utc),
            Prix=700,Statut=status,BudgetApproved=approved,BudgetVersion=1,BudgetSyncState=approved?"SYNCED":"BLOCKED"};
        db.Context.ReservationsSalles.Add(booking); await db.Context.SaveChangesAsync(); return booking;
    }
    private static SalleUpdateDto Update(int capacity) => new() {Nom="Changed",Adresse="Changed",Capacite=capacity,Prix=900};

    [Fact]
    public async Task CancelLastRoomWithGuestsIsRejectedWithoutCallingBudget()
    {
        await using var db=new TestDatabase(); var room=await Room(db); var booking=await Booking(db,room);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80; var budget=new ApprovingBudgetGateway();
        var controller=new ReservationsApiController(db.Context,budget,guests);
        var result=Assert.IsType<ConflictObjectResult>(await controller.Cancel(booking.Id));
        Assert.Contains("80",result.Value!.ToString());
        Assert.Equal("CONFIRMEE",booking.Statut); Assert.Equal("BLOCKED",booking.BudgetSyncState);
        Assert.Empty(budget.Calls);
        // Retry after guest registrations have been reduced: same proposal, no duplicate booking.
        guests.Counts[1]=0;
        Assert.IsType<OkObjectResult>(await controller.SynchronizeBudget(booking.Id));
        Assert.Equal("ANNULEE",booking.Statut); Assert.Single(budget.Calls);
    }
    [Fact]
    public async Task CancelRoomAllowedWhenOtherApprovedRoomHasExactlyEnoughSeats()
    {
        await using var db=new TestDatabase(); var first=await Room(db); var other=await Room(db,80);
        var booking=await Booking(db,first); await Booking(db,other);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80;
        var result=await new ReservationsApiController(db.Context,new ApprovingBudgetGateway(),guests).Cancel(booking.Id);
        Assert.IsType<OkObjectResult>(result); Assert.Equal("ANNULEE",booking.Statut);
    }
    [Theory]
    [InlineData(false,"CONFIRMEE")]
    [InlineData(true,"ANNULEE")]
    public async Task UnapprovedOrCancelledOtherRoomProvidesNoReplacementSeats(bool approved,string status)
    {
        await using var db=new TestDatabase(); var first=await Room(db); var other=await Room(db,100);
        var booking=await Booking(db,first); await Booking(db,other,approved:approved,status:status);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=1;
        Assert.IsType<ConflictObjectResult>(await new ReservationsApiController(db.Context,new ApprovingBudgetGateway(),guests).Cancel(booking.Id));
    }
    [Fact]
    public async Task CancellingOneOfTwoBookingsForSameRoomKeepsItsCapacity()
    {
        await using var db=new TestDatabase(); var room=await Room(db); var booking=await Booking(db,room); await Booking(db,room);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80;
        Assert.IsType<OkObjectResult>(await new ReservationsApiController(db.Context,new ApprovingBudgetGateway(),guests).Cancel(booking.Id));
        Assert.Empty(guests.Calls); // capacity unchanged, no external check necessary.
    }
    [Fact]
    public async Task NewFirstRoomCannotIntroduceALimitBelowExistingGuests()
    {
        await using var db=new TestDatabase(); var room=await Room(db,50);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80; var budget=new ApprovingBudgetGateway();
        var result=await new ReservationsApiController(db.Context,budget,guests).Create(new ReservationCreateDto {
            SalleId=room.Id,EvenementId=1,Prix=700,DateDebut=new DateTime(2082,1,10,9,0,0,DateTimeKind.Utc),DateFin=new DateTime(2082,1,10,18,0,0,DateTimeKind.Utc)});
        Assert.IsType<ConflictObjectResult>(result.Result); Assert.Empty(budget.Calls);
        Assert.False((await db.Context.ReservationsSalles.SingleAsync()).BudgetApproved);
    }
    [Theory]
    [InlineData(79,409)] [InlineData(80,204)] [InlineData(90,204)]
    public async Task CapacityReductionRespectsOccupiedSeats(int capacity,int expected)
    {
        await using var db=new TestDatabase(); var room=await Room(db); await Booking(db,room);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80;
        var result=await new SallesApiController(db.Context,guests).Update(room.Id,Update(capacity));
        int code=result is StatusCodeResult status?status.StatusCode:((ObjectResult)result).StatusCode!.Value;
        Assert.Equal(expected,code);
        Assert.Equal(expected==204?capacity:100,room.Capacite);
        if(expected==409) Assert.Equal("Capacity room",room.Nom);
    }

    [Fact]
    public async Task ReducingSharedRoomChecksEveryAffectedEventBeforeChangingAnything()
    {
        await using var db=new TestDatabase(); var room=await Room(db); await Booking(db,room,1); await Booking(db,room,2);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=20; guests.Counts[2]=80;
        var result=Assert.IsType<ObjectResult>(await new SallesApiController(db.Context,guests).Update(room.Id,Update(50)));
        Assert.Equal(409,result.StatusCode); Assert.Equal(100,room.Capacite); Assert.Equal(2,guests.Calls.Count);
    }
    [Fact]
    public async Task SameRoomIsNotCountedTwiceForCapacityReduction()
    {
        await using var db=new TestDatabase(); var room=await Room(db); await Booking(db,room); await Booking(db,room);
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80;
        var result=Assert.IsType<ObjectResult>(await new SallesApiController(db.Context,guests).Update(room.Id,Update(50)));
        Assert.Equal(409,result.StatusCode); Assert.Equal(100,room.Capacite);
    }
    [Fact]
    public async Task ReductionCanUseSeatsFromAnotherApprovedRoom()
    {
        await using var db=new TestDatabase(); var room=await Room(db); await Booking(db,room); await Booking(db,await Room(db,40));
        var guests=new FakeGuestOccupancyGateway(); guests.Counts[1]=80;
        Assert.IsType<NoContentResult>(await new SallesApiController(db.Context,guests).Update(room.Id,Update(40)));
    }
    [Fact]
    public async Task GuestServiceFailureRejectsReductionAndCancellationButNotCapacityIncrease()
    {
        await using var db=new TestDatabase(); var room=await Room(db); var booking=await Booking(db,room);
        var guests=new FakeGuestOccupancyGateway {OnCount=_=>throw new RemoteServiceFailure(503,"Unavailable")};
        var result=Assert.IsType<ObjectResult>(await new SallesApiController(db.Context,guests).Update(room.Id,Update(50)));
        Assert.Equal(503,result.StatusCode); Assert.Equal(100,room.Capacite);
        var cancelled=Assert.IsType<ObjectResult>(await new ReservationsApiController(db.Context,new ApprovingBudgetGateway(),guests).Cancel(booking.Id));
        Assert.Equal(503,cancelled.StatusCode); Assert.Equal("CONFIRMEE",booking.Statut);
        Assert.IsType<NoContentResult>(await new SallesApiController(db.Context,guests).Update(room.Id,Update(120)));
    }
}
