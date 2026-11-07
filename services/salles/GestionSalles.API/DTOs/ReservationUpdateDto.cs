using System.ComponentModel.DataAnnotations;

namespace GestionSalles.API.DTOs;

public class ReservationUpdateDto
{
    [Required]
    public DateTime DateDebut { get; set; }

    [Required]
    public DateTime DateFin { get; set; }

    [Range(typeof(decimal), "0.01", "9999999999999999")]
    public decimal Prix { get; set; }
}