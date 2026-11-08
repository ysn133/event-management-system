using GestionSalles.API.Controllers.API;
using GestionSalles.API.DTOs;
using GestionSalles.API.Enums;
using GestionSalles.API.Models;
using GestionSalles.Tests.Infrastructure;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.Tests.Controllers;

public class ReservationsApiControllerTests
{
    private static readonly DateTime Start =
        new(2026, 12, 15, 9, 0, 0, DateTimeKind.Utc);
    private static readonly DateTime End =
        new(2026, 12, 15, 18, 0, 0, DateTimeKind.Utc);

    private static Salle MakeSalle() => new()
    {
        Nom = "Salle A23",
        Capacite = 50,
        Adresse = "FSA Agadir",
        Prix = 800m
    };

    private static ReservationSalle MakeReservation(
        DateTime? start = null, DateTime? end = null, string status = "EN_ATTENTE") => new()
    {
        DateDebut = start ?? Start,
        DateFin = end ?? End,
        Prix = 800m,
        Statut = status,
        EvenementId = 1
    };

    [Fact]
    public async Task GetByEvenement_RetourneReservationsDeLEvenement()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation());
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();

        var action = await new ReservationsApiController(db.Context).GetByEvenement(1);

        var result = Assert.IsType<OkObjectResult>(action.Result);
        var items = Assert.IsAssignableFrom<IEnumerable<ReservationResponseDto>>(result.Value);
        Assert.Single(items);
        Assert.Equal("Salle A23", items.Single().NomSalle);
    }

    [Fact]
    public async Task GetById_ReservationExistante_RetourneOk()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation());
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();
        var id = await db.Context.ReservationsSalles.Select(r => r.Id).SingleAsync();

        var action = await new ReservationsApiController(db.Context).GetById(id);

        var result = Assert.IsType<OkObjectResult>(action.Result);
        var dto = Assert.IsType<ReservationResponseDto>(result.Value);
        Assert.Equal(id, dto.Id);
        Assert.Equal("Salle A23", dto.NomSalle);
    }

    [Fact]
    public async Task GetById_ReservationInexistante_RetourneNotFound()
    {
        await using var db = new TestDatabase();

        var action = await new ReservationsApiController(db.Context).GetById(999);

        Assert.IsType<NotFoundObjectResult>(action.Result);
    }

    [Fact]
    public async Task Create_DateFinAvantDebut_RetourneBadRequest()
    {
        await using var db = new TestDatabase();
        var dto = new ReservationCreateDto
        {
            SalleId = 1, EvenementId = 1,
            DateDebut = End, DateFin = Start, Prix = 800m
        };

        var action = await new ReservationsApiController(db.Context).Create(dto);

        Assert.IsType<BadRequestObjectResult>(action.Result);
        Assert.Empty(await db.Context.ReservationsSalles.ToListAsync());
    }

    [Fact]
    public async Task Create_DatesNonUtc_RetourneBadRequest()
    {
        await using var db = new TestDatabase();
        var dto = new ReservationCreateDto
        {
            SalleId = 1, EvenementId = 1,
            DateDebut = DateTime.SpecifyKind(Start, DateTimeKind.Local),
            DateFin = DateTime.SpecifyKind(End, DateTimeKind.Local),
            Prix = 800m
        };

        var action = await new ReservationsApiController(db.Context).Create(dto);

        Assert.IsType<BadRequestObjectResult>(action.Result);
    }

    [Fact]
    public async Task Create_SalleInexistante_RetourneNotFound()
    {
        await using var db = new TestDatabase();
        var dto = new ReservationCreateDto
        {
            SalleId = 999, EvenementId = 1,
            DateDebut = Start, DateFin = End, Prix = 800m
        };

        var action = await new ReservationsApiController(db.Context).Create(dto);

        Assert.IsType<NotFoundObjectResult>(action.Result);
    }

    [Fact]
    public async Task Update_ReservationExistante_ModifieDatesEtPrix()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation());
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();
        var reservation = await db.Context.ReservationsSalles.SingleAsync();
        var newEnd = new DateTime(2026, 12, 15, 17, 0, 0, DateTimeKind.Utc);

        var dto = new ReservationUpdateDto
        {
            DateDebut = Start, DateFin = newEnd, Prix = 900m
        };

        var result = await new ReservationsApiController(db.Context)
            .Update(reservation.Id, dto);

        Assert.IsType<OkObjectResult>(result);
        var updated = await db.Context.ReservationsSalles.FindAsync(reservation.Id);
        Assert.NotNull(updated);
        Assert.Equal(newEnd, updated.DateFin);
        Assert.Equal(900m, updated.Prix);
    }

    [Fact]
    public async Task Update_ReservationInexistante_RetourneNotFound()
    {
        await using var db = new TestDatabase();
        var dto = new ReservationUpdateDto
        {
            DateDebut = Start, DateFin = End, Prix = 900m
        };

        var result = await new ReservationsApiController(db.Context).Update(999, dto);

        Assert.IsType<NotFoundObjectResult>(result);
    }

    [Fact]
    public async Task Update_ReservationAnnulee_RetourneBadRequest()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation(status: "ANNULEE"));
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();
        var reservation = await db.Context.ReservationsSalles.SingleAsync();

        var dto = new ReservationUpdateDto
        {
            DateDebut = Start, DateFin = End, Prix = 900m
        };

        var result = await new ReservationsApiController(db.Context)
            .Update(reservation.Id, dto);

        Assert.IsType<BadRequestObjectResult>(result);
    }

    [Fact]
    public async Task Update_CheauchementAvecAutreReservation_RetourneConflict()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation());
        salle.Reservations.Add(MakeReservation(
            new DateTime(2026, 12, 15, 17, 0, 0, DateTimeKind.Utc),
            new DateTime(2026, 12, 15, 20, 0, 0, DateTimeKind.Utc)));
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();
        var items = await db.Context.ReservationsSalles.OrderBy(r => r.Id).ToListAsync();

        var dto = new ReservationUpdateDto
        {
            DateDebut = Start,
            DateFin = new DateTime(2026, 12, 15, 19, 0, 0, DateTimeKind.Utc),
            Prix = 900m
        };

        var result = await new ReservationsApiController(db.Context)
            .Update(items[0].Id, dto);

        Assert.IsType<ConflictObjectResult>(result);
    }

    [Fact]
    public async Task Cancel_ReservationExistante_ChangeStatutEnAnnulee()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation());
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();
        var reservation = await db.Context.ReservationsSalles.SingleAsync();

        var result = await new ReservationsApiController(db.Context)
            .Cancel(reservation.Id);

        Assert.IsType<OkObjectResult>(result);
        var updated = await db.Context.ReservationsSalles.FindAsync(reservation.Id);
        Assert.NotNull(updated);
        Assert.Equal(StatutReservation.ANNULEE.ToString(), updated.Statut);
    }

    [Fact]
    public async Task Cancel_ReservationInexistante_RetourneNotFound()
    {
        await using var db = new TestDatabase();

        var result = await new ReservationsApiController(db.Context).Cancel(999);

        Assert.IsType<NotFoundObjectResult>(result);
    }

    [Fact]
    public async Task Cancel_ReservationDejaAnnulee_RetourneBadRequest()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(MakeReservation(status: "ANNULEE"));
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();
        var reservation = await db.Context.ReservationsSalles.SingleAsync();

        var result = await new ReservationsApiController(db.Context)
            .Cancel(reservation.Id);

        Assert.IsType<BadRequestObjectResult>(result);
    }
}
