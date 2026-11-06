
using GestionSalles.API.Data;
using GestionSalles.API.DTOs;
using GestionSalles.API.Enums;
using GestionSalles.API.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.API.Controllers.API;

[ApiController]
[Route("api/salles")]
public class SallesApiController : ControllerBase
{
    private readonly ApplicationDbContext _context;

    public SallesApiController(ApplicationDbContext context)
    {
        _context = context;
    }

    // GET: api/salles
    [HttpGet]
    public async Task<ActionResult<IEnumerable<SalleResponseDto>>> GetAll()
    {
        var salles = await _context.Salles
            .Select(s => new SalleResponseDto
            {
                Id = s.Id,
                Nom = s.Nom,
                Capacite = s.Capacite,
                Adresse = s.Adresse,
                Prix = s.Prix,

                Disponibilite = s.Reservations.Any(
                    r => r.Statut != "ANNULEE"
                )
                ? DisponibiliteSalle.Reservee
                : DisponibiliteSalle.Disponible
            })
            .ToListAsync();

        return Ok(salles);
    }

    // GET: api/salles/1
    [HttpGet("{id:long}")]
    public async Task<ActionResult<SalleResponseDto>> GetById(long id)
    {
        var salle = await _context.Salles
            .Where(s => s.Id == id)
            .Select(s => new SalleResponseDto
            {
                Id = s.Id,
                Nom = s.Nom,
                Capacite = s.Capacite,
                Adresse = s.Adresse,
                Prix = s.Prix,

                Disponibilite = s.Reservations.Any(
                    r => r.Statut != "ANNULEE"
                )
                ? DisponibiliteSalle.Reservee
                : DisponibiliteSalle.Disponible
            })
            .FirstOrDefaultAsync();

        if (salle == null)
            return NotFound(new { message = "Salle introuvable." });

        return Ok(salle);
    }

    // POST: api/salles
    [HttpPost]
    public async Task<ActionResult<SalleResponseDto>> Create(
        SalleCreateDto dto)
    {
        var salle = new Salle
        {
            Nom = dto.Nom,
            Capacite = dto.Capacite,
            Adresse = dto.Adresse,
            Prix = dto.Prix
        };

        _context.Salles.Add(salle);
        await _context.SaveChangesAsync();

        var response = new SalleResponseDto
        {
            Id = salle.Id,
            Nom = salle.Nom,
            Capacite = salle.Capacite,
            Adresse = salle.Adresse,
            Prix = salle.Prix,
            Disponibilite = DisponibiliteSalle.Disponible
        };

        return CreatedAtAction(
            nameof(GetById),
            new { id = salle.Id },
            response
        );
    }

    // PUT: api/salles/1
    [HttpPut("{id:long}")]
    public async Task<IActionResult> Update(
        long id,
        SalleUpdateDto dto)
    {
        var salle = await _context.Salles.FindAsync(id);

        if (salle == null)
            return NotFound(new { message = "Salle introuvable." });

        salle.Nom = dto.Nom;
        salle.Capacite = dto.Capacite;
        salle.Adresse = dto.Adresse;
        salle.Prix = dto.Prix;

        await _context.SaveChangesAsync();

        return NoContent();
    }

    // DELETE: api/salles/1
    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long id)
    {
        var salle = await _context.Salles
            .Include(s => s.Reservations)
            .FirstOrDefaultAsync(s => s.Id == id);

        if (salle == null)
            return NotFound(new { message = "Salle introuvable." });

        if (salle.Reservations.Any(r => r.Statut != "ANNULEE"))
        {
            return Conflict(new
            {
                message = "Impossible de supprimer cette salle : elle possède des réservations non annulées."
            });
        }

        _context.Salles.Remove(salle);
        await _context.SaveChangesAsync();

        return NoContent();
    }

    // GET: api/salles/test-connexion
    [HttpGet("test-connexion")]
    public async Task<IActionResult> TestConnexion()
    {
        try
        {
            await _context.Database.OpenConnectionAsync();

            return Ok(new
            {
                connected = true,
                message = "Connexion Supabase réussie."
            });
        }
        catch (Exception ex)
        {
            return StatusCode(500, new
            {
                connected = false,
                error = ex.GetBaseException().Message
            });
        }
        finally
        {
            if (_context.Database.GetDbConnection().State ==
                System.Data.ConnectionState.Open)
            {
                await _context.Database.CloseConnectionAsync();
            }
        }
    }
}
