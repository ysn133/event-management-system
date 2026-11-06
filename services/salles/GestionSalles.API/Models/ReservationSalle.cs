
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace GestionSalles.API.Models;

[Table("reservations_salle")]
public class ReservationSalle
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Column("date_debut")]
    public DateTime DateDebut { get; set; }

    [Column("date_fin")]
    public DateTime DateFin { get; set; }

    [Column("prix", TypeName = "numeric")]
    public decimal Prix { get; set; }

    [Required]
    [Column("statut")]
    public string Statut { get; set; } = "EN_ATTENTE";

    [Column("salle_id")]
    public long SalleId { get; set; }

    public Salle Salle { get; set; } = null!;

    [Column("evenement_id")]
    public long EvenementId { get; set; }
}
