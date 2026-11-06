
using System.ComponentModel.DataAnnotations;

namespace GestionSalles.API.DTOs;

public class SalleCreateDto
{
    [Required(ErrorMessage = "Le nom est obligatoire.")]
    [StringLength(150)]
    public string Nom { get; set; } = string.Empty;

    [Range(1, int.MaxValue,
        ErrorMessage = "La capacité doit être supérieure à zéro.")]
    public int Capacite { get; set; }

    [Required(ErrorMessage = "L'adresse est obligatoire.")]
    public string Adresse { get; set; } = string.Empty;

    [Range(typeof(decimal), "0.01", "9999999999999999",
        ErrorMessage = "Le prix doit être supérieur à zéro.")]
    public decimal Prix { get; set; }
}
