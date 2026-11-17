using System.Text.Json;
using GestionSalles.API.Data;
using GestionSalles.API.DTOs;
using GestionSalles.API.Models;
using GestionSalles.API.Integration;
using Microsoft.EntityFrameworkCore;
namespace GestionSalles.API.Services;

public record RoomChange(DateTime DateDebut, DateTime DateFin, decimal Prix, string Statut);
public record RoomOperation(int Status, ReservationResponseDto? Reservation = null, string? Error = null, long? PendingId = null);

public class ReservationBudgetWorkflow(ApplicationDbContext db, IRoomBudgetGateway gateway, RoomCapacityGuard capacity)
{
    public static ReservationResponseDto Response(ReservationSalle r) => new() {
        Id=r.Id, SalleId=r.SalleId, EvenementId=r.EvenementId, NomSalle=r.Salle.Nom,
        DateDebut=r.DateDebut, DateFin=r.DateFin, Prix=r.Prix, Statut=r.Statut,
        BudgetVersion=r.BudgetVersion, BudgetSyncState=r.BudgetSyncState, BudgetApproved=r.BudgetApproved
    };
    private static RoomChange Snapshot(ReservationSalle r) => new(DateTime.SpecifyKind(r.DateDebut,DateTimeKind.Utc),DateTime.SpecifyKind(r.DateFin,DateTimeKind.Utc),r.Prix,r.Statut);
    private static string? Validate(RoomChange p) => p.DateFin<=p.DateDebut ? "La date de fin doit être après la date de début."
        : p.DateDebut.Kind!=DateTimeKind.Utc || p.DateFin.Kind!=DateTimeKind.Utc ? "Les dates doivent être envoyées en UTC."
        : p.Prix<=0 || decimal.Round(p.Prix,2)!=p.Prix ? "Le prix doit être positif, avec au maximum deux décimales." : null;
    private async Task LockRoom(long id)
    {
        if (db.Database.IsNpgsql())
            await db.Database.ExecuteSqlInterpolatedAsync($"SELECT id FROM salles WHERE id={id} FOR UPDATE");
    }
    private async Task<bool> Conflict(long roomId,long except,RoomChange proposal)
    {
        if (proposal.Statut=="ANNULEE") return false;
        // Pending proposals retain their old slot and reserve a room while a
        // Budget response is uncertain. They must be resolved before another change.
        return await db.ReservationsSalles.AnyAsync(r => r.SalleId==roomId && r.Id!=except &&
            (r.BudgetSyncState=="PENDING" || (r.Statut!="ANNULEE" && r.BudgetApproved &&
            r.DateDebut<proposal.DateFin && r.DateFin>proposal.DateDebut)));
    }
    public async Task<RoomOperation> Create(ReservationCreateDto dto,string authorization)
    {
        var proposal=new RoomChange(dto.DateDebut,dto.DateFin,dto.Prix,"EN_ATTENTE");
        if (Validate(proposal) is string invalid) return new(400,Error:invalid);
        if (dto.RequestId==Guid.Empty) return new(400,Error:"Identifiant de demande invalide.");
        var salle=await db.Salles.FindAsync(dto.SalleId);
        if (salle is null) return new(404,Error:"Salle introuvable.");
        try { await gateway.ValidateUser(authorization); await gateway.ValidateEvent(dto.EvenementId,authorization); }
        catch (RemoteServiceFailure error) { return new(error.Status,Error:error.Message); }
        long id;
        await using (var tx=await db.Database.BeginTransactionAsync())
        {
            await LockRoom(dto.SalleId);
            var existing=await db.ReservationsSalles.Include(r=>r.Salle).FirstOrDefaultAsync(r=>r.RequestId==dto.RequestId);
            if (existing is not null)
            {
                var original=existing.PendingPayload is null ? Snapshot(existing) : JsonSerializer.Deserialize<RoomChange>(existing.PendingPayload)!;
                if (existing.SalleId!=dto.SalleId || existing.EvenementId!=dto.EvenementId || original.DateDebut!=proposal.DateDebut || original.DateFin!=proposal.DateFin || original.Prix!=proposal.Prix)
                    return new(409,Error:"Cette demande est déjà utilisée pour une autre réservation.");
                id=existing.Id;
            }
            else
            {
                if (await Conflict(dto.SalleId,0,proposal)) return new(409,Error:"Cette salle est déjà réservée ou possède une synchronisation en attente.");
                var reservation=new ReservationSalle { Salle=salle,SalleId=dto.SalleId,EvenementId=dto.EvenementId,
                    DateDebut=dto.DateDebut,DateFin=dto.DateFin,Prix=dto.Prix,Statut="EN_ATTENTE",RequestId=dto.RequestId,
                    BudgetApproved=false,BudgetVersion=1,BudgetSyncState="PENDING",PendingPayload=JsonSerializer.Serialize(proposal) };
                db.ReservationsSalles.Add(reservation); await db.SaveChangesAsync(); id=reservation.Id;
            }
            await tx.CommitAsync();
        }
        var result=await Synchronize(id,authorization,validateUser:false);
        return result.Status==200 ? result with { Status=201 } : result;
    }
    public async Task<RoomOperation> Change(long id,ReservationUpdateDto? dto,string? status,string authorization)
    {
        var reservation=await db.ReservationsSalles.Include(r=>r.Salle).FirstOrDefaultAsync(r=>r.Id==id);
        if (reservation is null) return new(404,Error:"Réservation introuvable.");
        try { await gateway.ValidateUser(authorization); }
        catch (RemoteServiceFailure error) { return new(error.Status,Error:error.Message); }
        await using (var tx=await db.Database.BeginTransactionAsync())
        {
            await LockRoom(reservation.SalleId); await db.Entry(reservation).ReloadAsync();
            if (reservation.BudgetSyncState=="PENDING") return new(409,Error:"Réessayez la synchronisation en attente avant de modifier cette réservation.");
            if (reservation.Statut=="ANNULEE") return new(400,Error:"La réservation est déjà annulée.");
            if (status=="ANNULEE" && !reservation.BudgetApproved && reservation.BudgetSyncState=="BLOCKED")
            {
                // A definitively refused proposal never created an approved commitment.
                // Uncertain PENDING requests must be resolved instead of discarded.
                reservation.Statut="ANNULEE"; reservation.BudgetSyncState="SYNCED"; reservation.PendingPayload=null;
                await db.SaveChangesAsync(); await tx.CommitAsync();
                return new(200,Response(reservation));
            }
            if (status=="CONFIRMEE" && reservation.Statut=="CONFIRMEE") return new(400,Error:"Cette réservation est déjà confirmée.");
            if (dto?.Version is long version && version!=reservation.BudgetVersion) return new(409,Error:"La réservation a changé. Actualisez la liste.");
            var proposal=new RoomChange(dto?.DateDebut??DateTime.SpecifyKind(reservation.DateDebut,DateTimeKind.Utc),dto?.DateFin??DateTime.SpecifyKind(reservation.DateFin,DateTimeKind.Utc),dto?.Prix??reservation.Prix,status??reservation.Statut);
            if (Validate(proposal) is string invalid) return new(400,Error:invalid);
            if (await Conflict(reservation.SalleId,id,proposal)) return new(409,Error:"Cette salle est déjà réservée sur cette période ou attend une synchronisation.");
            reservation.BudgetVersion++; reservation.BudgetSyncState="PENDING"; reservation.PendingPayload=JsonSerializer.Serialize(proposal);
            await db.SaveChangesAsync(); await tx.CommitAsync();
        }
        return await Synchronize(id,authorization,validateUser:false);
    }
    public async Task<RoomOperation> Synchronize(long id,string authorization,bool validateUser=true)
    {
        if (validateUser)
        {
            try { await gateway.ValidateUser(authorization); }
            catch (RemoteServiceFailure error) { return new(error.Status,Error:error.Message); }
        }
        var reservation=await db.ReservationsSalles.Include(r=>r.Salle).FirstOrDefaultAsync(r=>r.Id==id);
        if (reservation is null) return new(404,Error:"Réservation introuvable.");
        try
        {
            await using var tx=await db.Database.BeginTransactionAsync();
            await LockRoom(reservation.SalleId); await db.Entry(reservation).ReloadAsync();
            if (reservation.PendingPayload is null) return new(200,Response(reservation));
            await db.Entry(reservation.Salle).ReloadAsync();
            var proposal=JsonSerializer.Deserialize<RoomChange>(reservation.PendingPayload)!;
            if (await Conflict(reservation.SalleId,id,proposal)) return new(409,Error:"Cette salle est déjà réservée sur cette période.",PendingId:id);
            try
            {
                await capacity.ReservationChange(reservation,proposal,authorization);
                await gateway.Approve(reservation,proposal,authorization);
            }
            catch (RemoteServiceFailure error)
            {
                if (error.Definitive) { reservation.BudgetSyncState="BLOCKED"; await db.SaveChangesAsync(); }
                await tx.CommitAsync();
                return new(error.Status,Error:error.Message,PendingId:id);
            }
            reservation.DateDebut=proposal.DateDebut; reservation.DateFin=proposal.DateFin;
            reservation.Prix=proposal.Prix; reservation.Statut=proposal.Statut;
            reservation.BudgetApproved=true; reservation.BudgetSyncState="SYNCED"; reservation.PendingPayload=null;
            await db.SaveChangesAsync(); await tx.CommitAsync();
            return new(200,Response(reservation));
        }
        catch (Exception error) when (error is DbUpdateException or System.Data.Common.DbException)
        {
            return new(503,Error:"Synchronisation interrompue. Réessayez la même réservation.",PendingId:id);
        }
    }
}
