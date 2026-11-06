
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace GestionSalles.API.Models;

[Table("salles")]
public class Salle
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [Column("nom")]
    [StringLength(150)]
    public string Nom { get; set; } = string.Empty;

    [Required]
    [Column("capacite")]
    [Range(1, int.MaxValue)]
    public int Capacite { get; set; }

    [Required]
    [Column("adresse")]
    public string Adresse { get; set; } = string.Empty;

    [Required]
    [Column("prix", TypeName = "numeric")]
    [Range(typeof(decimal), "0.01", "9999999999999999")]
    public decimal Prix { get; set; }

    public ICollection<ReservationSalle> Reservations { get; set; }
        = new List<ReservationSalle>();
}
