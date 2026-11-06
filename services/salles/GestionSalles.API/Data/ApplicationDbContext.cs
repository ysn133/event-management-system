
using GestionSalles.API.Models;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.API.Data;

public class ApplicationDbContext : DbContext
{
    public ApplicationDbContext(
        DbContextOptions<ApplicationDbContext> options)
        : base(options)
    {
    }

    public DbSet<Salle> Salles { get; set; }

    public DbSet<ReservationSalle> ReservationsSalles { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        base.OnModelCreating(modelBuilder);

        // Table Salles
        modelBuilder.Entity<Salle>()
            .ToTable("salles");

        // Table Reservations
        modelBuilder.Entity<ReservationSalle>()
            .ToTable("reservations_salle");

        // Relation : une salle possède plusieurs réservations
        modelBuilder.Entity<Salle>()
            .HasMany(s => s.Reservations)
            .WithOne(r => r.Salle)
            .HasForeignKey(r => r.SalleId)
            .OnDelete(DeleteBehavior.Restrict);

        // Clé primaire Salle
        modelBuilder.Entity<Salle>()
            .HasKey(s => s.Id);

        // Clé primaire ReservationSalle
        modelBuilder.Entity<ReservationSalle>()
            .HasKey(r => r.Id);

        // Configuration de la clé étrangère vers Evenements
        modelBuilder.Entity<ReservationSalle>()
            .Property(r => r.EvenementId)
            .HasColumnName("evenement_id");
    }
}
