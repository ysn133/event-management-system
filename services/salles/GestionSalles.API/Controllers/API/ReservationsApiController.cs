using GestionSalles.API.Data;
using GestionSalles.API.DTOs;
using GestionSalles.API.Integration;
using GestionSalles.API.Models;
using GestionSalles.API.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.API.Controllers.API;

[ApiController]
[Route("api/reservations")]
public class ReservationsApiController : ControllerBase
{
    private readonly ApplicationDbContext _context;
    private readonly ReservationBudgetWorkflow _workflow;
    public ReservationsApiController(ApplicationDbContext context, IRoomBudgetGateway? gateway = null, IGuestOccupancyGateway? guests = null)
    {
        _context=context;
        _workflow=new ReservationBudgetWorkflow(context,gateway ?? new UnavailableGateway(),
            new RoomCapacityGuard(context,guests ?? new UnavailableGuestOccupancyGateway()));
    }
    private string Authorization => ControllerContext.HttpContext?.Request.Headers.Authorization.ToString() ?? "";
    private IActionResult Result(RoomOperation outcome)
    {
        if (outcome.Status==200) return Ok(outcome.Reservation);
        var error=new { message=outcome.Error, reservationId=outcome.PendingId, pending=outcome.PendingId.HasValue };
        return outcome.Status switch {
            400 => BadRequest(error), 401 => Unauthorized(error), 404 => NotFound(error),
            409 => Conflict(error), _ => StatusCode(outcome.Status,error)
        };
    }
    [HttpGet("evenement/{evenementId:long}")]
    public async Task<ActionResult<IEnumerable<ReservationResponseDto>>> GetByEvenement(long evenementId)
    {
        var rows=await _context.ReservationsSalles.AsNoTracking().Include(r=>r.Salle)
            .Where(r=>r.EvenementId==evenementId).OrderBy(r=>r.Id).ToListAsync();
        return Ok(rows.Select(ReservationBudgetWorkflow.Response).ToList());
    }
    [HttpGet("{id:long}")]
    public async Task<ActionResult<ReservationResponseDto>> GetById(long id)
    {
        var row=await _context.ReservationsSalles.AsNoTracking().Include(r=>r.Salle).FirstOrDefaultAsync(r=>r.Id==id);
        return row is null ? NotFound(new { message="Réservation introuvable." }) : Ok(ReservationBudgetWorkflow.Response(row));
    }
    [HttpPost]
    public async Task<ActionResult<ReservationResponseDto>> Create(ReservationCreateDto dto)
    {
        var result=await _workflow.Create(dto,Authorization);
        if (result.Status==201)
            return CreatedAtAction(nameof(GetById),new { id=result.Reservation!.Id },result.Reservation);
        return (ActionResult)Result(result);
    }
    [HttpPut("{id:long}")]
    public async Task<IActionResult> Update(long id,ReservationUpdateDto dto)
        => Result(await _workflow.Change(id,dto,null,Authorization));

    [HttpPatch("{id:long}/confirmer")]
    public async Task<IActionResult> Confirm(long id)
        => Result(await _workflow.Change(id,null,"CONFIRMEE",Authorization));

    [HttpPatch("{id:long}/annuler")]
    public async Task<IActionResult> Cancel(long id)
        => Result(await _workflow.Change(id,null,"ANNULEE",Authorization));

    [HttpPost("{id:long}/synchroniser-budget")]
    public async Task<IActionResult> SynchronizeBudget(long id)
        => Result(await _workflow.Synchronize(id,Authorization));

    [HttpDelete("{id:long}")]
    public async Task<IActionResult> Delete(long id)
    {
        var row=await _context.ReservationsSalles.FindAsync(id);
        if (row is null) return NotFound(new { message="Réservation introuvable." });
        if (row.Statut!="ANNULEE") return BadRequest(new { message="Seules les réservations annulées peuvent être supprimées." });
        if (row.BudgetSyncState!="SYNCED") return Conflict(new { message="La synchronisation du budget doit être terminée avant la suppression." });
        if (row.BudgetApproved) return Conflict(new { message="Cette réservation est conservée dans l’historique financier. Son annulation ne supprime pas les paiements." });
        _context.ReservationsSalles.Remove(row);
        await _context.SaveChangesAsync();
        return Ok(new { message="Réservation supprimée définitivement." });
    }

    private class UnavailableGateway : IRoomBudgetGateway
    {
        public Task ValidateUser(string authorization) => throw new RemoteServiceFailure(503,"Intégration Budget non configurée.");
        public Task ValidateEvent(long id,string authorization) => ValidateUser(authorization);
        public Task Approve(ReservationSalle reservation,RoomChange proposal,string authorization) => ValidateUser(authorization);
    }
}
