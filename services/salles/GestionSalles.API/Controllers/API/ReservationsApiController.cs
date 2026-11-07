
using GestionSalles.API.Data;
using GestionSalles.API.DTOs;
using GestionSalles.API.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using GestionSalles.API.Enums;

namespace GestionSalles.API.Controllers.API;

[ApiController]
[Route("api/reservations")]
public class ReservationsApiController : ControllerBase
{
    private readonly ApplicationDbContext _context;

    public ReservationsApiController(ApplicationDbContext context)
    {
        _context = context;
    }

    // GET: api/reservations/evenement/1
    [HttpGet("evenement/{evenementId:long}")]
    public async Task<ActionResult<IEnumerable<ReservationResponseDto>>>
        GetByEvenement(long evenementId)
    {
        var reservations = await _context.ReservationsSalles
            .AsNoTracking()
            .Where(r => r.EvenementId == evenementId)
            .Select(r => new ReservationResponseDto
            {
                Id = r.Id,
                SalleId = r.SalleId,
                EvenementId = r.EvenementId,
                NomSalle = r.Salle.Nom,
                DateDebut = r.DateDebut,
                DateFin = r.DateFin,
                Prix = r.Prix,
                Statut = r.Statut
            })
            .ToListAsync();

        return Ok(reservations);
    }

    // POST: api/reservations
    [HttpPost]
    public async Task<ActionResult<ReservationResponseDto>> Create(
        ReservationCreateDto dto)
    {
        // Vérifier les dates
        if (dto.DateFin <= dto.DateDebut)
        {
            return BadRequest(new
            {
                message = "La date de fin doit être après la date de début."
            });
        }

        // Vérifier le format UTC pour PostgreSQL timestamptz
        if (dto.DateDebut.Kind != DateTimeKind.Utc ||
            dto.DateFin.Kind != DateTimeKind.Utc)
        {
            return BadRequest(new
            {
                message = "Les dates doivent être envoyées en UTC."
            });
        }

        // Vérifier que la salle existe
        var salle = await _context.Salles
            .AsNoTracking()
            .FirstOrDefaultAsync(s => s.Id == dto.SalleId);

        if (salle == null)
        {
            return NotFound(new
            {
                message = "Salle introuvable."
            });
        }

        // Vérifier que l'événement existe dans PostgreSQL local
        // sans créer un modèle Evenement.cs
        var connexion = _context.Database.GetDbConnection();

        var evenementExiste = false;

        await _context.Database.OpenConnectionAsync();

        try
        {
            await using var commande = connexion.CreateCommand();

            commande.CommandText =
                "SELECT EXISTS(SELECT 1 FROM public.evenements WHERE id = @id)";

            var parametre = commande.CreateParameter();
            parametre.ParameterName = "@id";
            parametre.Value = dto.EvenementId;

            commande.Parameters.Add(parametre);

            var resultat = await commande.ExecuteScalarAsync();
            evenementExiste = resultat is bool existe && existe;
        }
        finally
        {
            await _context.Database.CloseConnectionAsync();
        }

        if (!evenementExiste)
        {
            return BadRequest(new
            {
                message = "L'événement n'existe pas dans la base de données locale."
            });
        }

        // Vérifier les chevauchements de dates
        var conflit = await _context.ReservationsSalles
            .AnyAsync(r =>
                r.SalleId == dto.SalleId &&
                r.Statut != "ANNULEE" &&
                r.DateDebut < dto.DateFin &&
                r.DateFin > dto.DateDebut);

        if (conflit)
        {
            return Conflict(new
            {
                message = "Cette salle est déjà réservée sur cette période."
            });
        }

        // Enregistrer la réservation
        var reservation = new ReservationSalle
        {
            SalleId = dto.SalleId,
            EvenementId = dto.EvenementId,
            DateDebut = dto.DateDebut,
            DateFin = dto.DateFin,
            Prix = dto.Prix,
            Statut = "EN_ATTENTE"
        };

        _context.ReservationsSalles.Add(reservation);
        await _context.SaveChangesAsync();

        var response = new ReservationResponseDto
        {
            Id = reservation.Id,
            SalleId = reservation.SalleId,
            EvenementId = reservation.EvenementId,
            NomSalle = salle.Nom,
            DateDebut = reservation.DateDebut,
            DateFin = reservation.DateFin,
            Prix = reservation.Prix,
            Statut = reservation.Statut
        };

        return CreatedAtAction(
            nameof(GetByEvenement),
            new { evenementId = reservation.EvenementId },
            response
        );
    }

    // GET: api/reservations/1
[HttpGet("{id:long}")]
public async Task<ActionResult<ReservationResponseDto>> GetById(long id)
{
    var reservation = await _context.ReservationsSalles
        .AsNoTracking()
        .Where(r => r.Id == id)
        .Select(r => new ReservationResponseDto
        {
            Id = r.Id,
            SalleId = r.SalleId,
            EvenementId = r.EvenementId,
            NomSalle = r.Salle.Nom,
            DateDebut = r.DateDebut,
            DateFin = r.DateFin,
            Prix = r.Prix,
            Statut = r.Statut
        })
        .FirstOrDefaultAsync();

    if (reservation == null)
        return NotFound(new { message = "Réservation introuvable." });

    return Ok(reservation);
}
// PUT: api/reservations/1
[HttpPut("{id:long}")]
public async Task<IActionResult> Update(
    long id,
    ReservationUpdateDto dto)
{
    if (dto.DateFin <= dto.DateDebut)
        return BadRequest(new
        {
            message = "La date de fin doit être après la date de début."
        });

    if (dto.DateDebut.Kind != DateTimeKind.Utc ||
        dto.DateFin.Kind != DateTimeKind.Utc)
        return BadRequest(new
        {
            message = "Les dates doivent être envoyées en UTC."
        });

    var reservation = await _context.ReservationsSalles
        .FirstOrDefaultAsync(r => r.Id == id);

    if (reservation == null)
        return NotFound(new { message = "Réservation introuvable." });

    if (reservation.Statut == StatutReservation.ANNULEE.ToString())
        return BadRequest(new
        {
            message = "Impossible de modifier une réservation annulée."
        });

    var conflit = await _context.ReservationsSalles.AnyAsync(r =>
        r.Id != id &&
        r.SalleId == reservation.SalleId &&
        r.Statut != StatutReservation.ANNULEE.ToString() &&
        r.DateDebut < dto.DateFin &&
        r.DateFin > dto.DateDebut
    );

    if (conflit)
        return Conflict(new
        {
            message = "Cette salle est déjà réservée sur cette période."
        });

    reservation.DateDebut = dto.DateDebut;
    reservation.DateFin = dto.DateFin;
    reservation.Prix = dto.Prix;

    await _context.SaveChangesAsync();

    return Ok(new
    {
        message = "Réservation modifiée avec succès.",
        reservation.Id,
        reservation.DateDebut,
        reservation.DateFin,
        reservation.Prix,
        reservation.Statut
    });
}
// PATCH: api/reservations/1/annuler
[HttpPatch("{id:long}/annuler")]
public async Task<IActionResult> Cancel(long id)
{
    var reservation = await _context.ReservationsSalles
        .FirstOrDefaultAsync(r => r.Id == id);

    if (reservation == null)
        return NotFound(new { message = "Réservation introuvable." });

    if (reservation.Statut == StatutReservation.ANNULEE.ToString())
        return BadRequest(new
        {
            message = "Cette réservation est déjà annulée."
        });

    reservation.Statut = StatutReservation.ANNULEE.ToString();

    await _context.SaveChangesAsync();

    return Ok(new
    {
        message = "Réservation annulée avec succès.",
        reservation.Id,
        reservation.Statut
    });
}
}
