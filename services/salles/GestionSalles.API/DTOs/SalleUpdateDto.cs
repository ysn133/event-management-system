using System.ComponentModel.DataAnnotations;

namespace GestionSalles.API.DTOs;

public class SalleUpdateDto
{
    [Required]
    [StringLength(150)]
    public string Nom { get; set; } = string.Empty;

    [Range(1, int.MaxValue)]
    public int Capacite { get; set; }

    [Required]
    public string Adresse { get; set; } = string.Empty;

    [Range(typeof(decimal), "0.01", "9999999999999999")]
    public decimal Prix { get; set; }
}