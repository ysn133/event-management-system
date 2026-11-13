using GestionSalles.API.Controllers.API;
using GestionSalles.API.DTOs;
using GestionSalles.API.Enums;
using GestionSalles.API.Models;
using GestionSalles.Tests.Infrastructure;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.Tests.Controllers;

public class SallesApiControllerTests
{
    private static Salle MakeSalle(string nom = "Salle A") => new()
    {
        Nom = nom,
        Capacite = 50,
        Adresse = "Agadir",
        Prix = 800m
    };

    [Fact]
    public async Task GetAll_RetourneToutesLesSalles()
    {
        await using var db = new TestDatabase();
        db.Context.Salles.AddRange(MakeSalle(), MakeSalle("Salle B"));
        await db.Context.SaveChangesAsync();

        var action = await new SallesApiController(db.Context).GetAll();

        var result = Assert.IsType<OkObjectResult>(action.Result);
        var salles = Assert.IsAssignableFrom<IEnumerable<SalleResponseDto>>(result.Value);
        Assert.Equal(2, salles.Count());
    }

    [Fact]
    public async Task GetById_SalleExistante_RetourneOk()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();

        var action = await new SallesApiController(db.Context).GetById(salle.Id);

        var result = Assert.IsType<OkObjectResult>(action.Result);
        var dto = Assert.IsType<SalleResponseDto>(result.Value);
        Assert.Equal(salle.Id, dto.Id);
        Assert.Equal(DisponibiliteSalle.Disponible, dto.Disponibilite);
    }

    [Fact]
    public async Task GetById_SalleInexistante_RetourneNotFound()
    {
        await using var db = new TestDatabase();

        var action = await new SallesApiController(db.Context).GetById(999);

        Assert.IsType<NotFoundObjectResult>(action.Result);
    }

    [Fact]
    public async Task Create_DonneesValides_CreeLaSalle()
    {
        await using var db = new TestDatabase();
        var dto = new SalleCreateDto
        {
            Nom = "Salle conférence",
            Capacite = 120,
            Adresse = "Agadir",
            Prix = 1500m
        };

        var action = await new SallesApiController(db.Context).Create(dto);

        var result = Assert.IsType<CreatedAtActionResult>(action.Result);
        var response = Assert.IsType<SalleResponseDto>(result.Value);
        Assert.Equal("Salle conférence", response.Nom);
        Assert.Equal(1, await db.Context.Salles.CountAsync());
    }

    [Fact]
    public async Task Update_SalleExistante_ModifieLesInformations()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();

        var dto = new SalleUpdateDto
        {
            Nom = "Salle rénovée",
            Capacite = 80,
            Adresse = "Marrakech",
            Prix = 1000m
        };

        var result = await new SallesApiController(db.Context).Update(salle.Id, dto);

        Assert.IsType<NoContentResult>(result);
        var updated = await db.Context.Salles.FindAsync(salle.Id);
        Assert.NotNull(updated);
        Assert.Equal("Salle rénovée", updated.Nom);
        Assert.Equal(80, updated.Capacite);
        Assert.Equal(1000m, updated.Prix);
    }

    [Fact]
    public async Task Update_SalleInexistante_RetourneNotFound()
    {
        await using var db = new TestDatabase();
        var dto = new SalleUpdateDto
        {
            Nom = "Salle",
            Capacite = 10,
            Adresse = "Agadir",
            Prix = 100m
        };

        var result = await new SallesApiController(db.Context).Update(999, dto);

        Assert.IsType<NotFoundObjectResult>(result);
    }

    [Fact]
    public async Task Delete_SalleSansReservationActive_SupprimeLaSalle()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();

        var result = await new SallesApiController(db.Context).Delete(salle.Id);

        Assert.IsType<NoContentResult>(result);
        Assert.Null(await db.Context.Salles.FindAsync(salle.Id));
    }

    [Fact]
    public async Task Delete_SalleAvecReservationActive_RetourneConflict()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(new ReservationSalle
        {
            DateDebut = new DateTime(2026, 12, 15, 9, 0, 0, DateTimeKind.Utc),
            DateFin = new DateTime(2026, 12, 15, 18, 0, 0, DateTimeKind.Utc),
            Prix = 800m,
            Statut = "EN_ATTENTE",
            EvenementId = 1
        });
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();

        var result = await new SallesApiController(db.Context).Delete(salle.Id);

        Assert.IsType<ConflictObjectResult>(result);
        Assert.NotNull(await db.Context.Salles.FindAsync(salle.Id));
    }

    [Fact]
    public async Task GetAll_ReservationAnnulee_SalleResteDisponible()
    {
        await using var db = new TestDatabase();
        var salle = MakeSalle();
        salle.Reservations.Add(new ReservationSalle
        {
            DateDebut = new DateTime(2026, 12, 15, 9, 0, 0, DateTimeKind.Utc),
            DateFin = new DateTime(2026, 12, 15, 18, 0, 0, DateTimeKind.Utc),
            Prix = 800m,
            Statut = "ANNULEE",
            EvenementId = 1
        });
        db.Context.Salles.Add(salle);
        await db.Context.SaveChangesAsync();

        var action = await new SallesApiController(db.Context).GetAll();

        var result = Assert.IsType<OkObjectResult>(action.Result);
        var salles = Assert.IsAssignableFrom<IEnumerable<SalleResponseDto>>(result.Value);
        Assert.Equal(DisponibiliteSalle.Disponible, Assert.Single(salles).Disponibilite);
    }
}
