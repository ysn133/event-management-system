using System.ComponentModel.DataAnnotations;

namespace GestionSalles.API.DTOs;

public class ReservationCreateDto
{
    [Required]
    public long SalleId { get; set; }

    [Required]
    public long EvenementId { get; set; }

    [Required]
    public DateTime DateDebut { get; set; }

    [Required]
    public DateTime DateFin { get; set; }

    [Range(typeof(decimal), "0.01", "9999999999999999")]
    public decimal Prix { get; set; }
}