using GestionSalles.API.Enums;

namespace GestionSalles.API.DTOs;

public class SalleResponseDto
{
    public long Id { get; set; }
    public string Nom { get; set; } = string.Empty;
    public int Capacite { get; set; }
    public string Adresse { get; set; } = string.Empty;
    public decimal Prix { get; set; }
    public DisponibiliteSalle Disponibilite { get; set; }
}