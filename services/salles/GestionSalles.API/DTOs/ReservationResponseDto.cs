namespace GestionSalles.API.DTOs;

public class ReservationResponseDto
{
    public long Id { get; set; }

    public long SalleId { get; set; }

    public long EvenementId { get; set; }

    public string NomSalle { get; set; } = string.Empty;

    public DateTime DateDebut { get; set; }

    public DateTime DateFin { get; set; }

    public decimal Prix { get; set; }

    public string Statut { get; set; } = string.Empty;
}