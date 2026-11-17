using System.Net.Http.Json;
using System.Text.Json;
using GestionSalles.API.Models;
using GestionSalles.API.Services;
namespace GestionSalles.API.Integration;

public class HttpRoomBudgetGateway(HttpClient http, IConfiguration config) : IRoomBudgetGateway
{
    private string Url(string key, string fallback) => (config[key] ?? fallback).TrimEnd('/');
    private async Task Send(string url, string authorization, object? body = null)
    {
        if (!authorization.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            throw new RemoteServiceFailure(401, "Connectez-vous pour gérer les réservations.");
        using var request = new HttpRequestMessage(body is null ? HttpMethod.Get : HttpMethod.Put, url);
        request.Headers.TryAddWithoutValidation("Authorization", authorization);
        if (body is not null)
        {
            request.Headers.TryAddWithoutValidation("X-Service-Key", config["ROOM_BUDGET_SERVICE_KEY"] ?? "");
            request.Content = JsonContent.Create(body);
        }
        try
        {
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            using var response = await http.SendAsync(request, timeout.Token);
            if (response.IsSuccessStatusCode) return;
            string message = response.StatusCode == System.Net.HttpStatusCode.Unauthorized
                ? "Session expirée. Reconnectez-vous." : "Le service a refusé la demande.";
            try
            {
                using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
                if (json.RootElement.TryGetProperty("message", out var value)) message = value.GetString() ?? message;
            }
            catch (JsonException) { }
            int status = (int)response.StatusCode;
            throw new RemoteServiceFailure(status, message, new[] {400,401,403,404,409,422}.Contains(status));
        }
        catch (Exception error) when (error is HttpRequestException or OperationCanceledException)
        {
            throw new RemoteServiceFailure(503, "Un service est indisponible. Réessayez la synchronisation de la réservation.", false);
        }
    }
    public Task ValidateUser(string authorization) => Send(Url("AUTH_SERVICE_URL","http://localhost:5005")+"/api/auth/me",authorization);
    public Task ValidateEvent(long eventId,string authorization) => Send(Url("EVENT_SERVICE_URL","http://localhost:5001")+$"/api/evenements/{eventId}",authorization);
    public Task Approve(ReservationSalle reservation,RoomChange proposal,string authorization) => Send(
        Url("BUDGET_SERVICE_URL","http://localhost:5004")+$"/api/budgets/evenement/{reservation.EvenementId}/reservations-salle/{reservation.Id}",
        authorization,new {
            description = "Réservation - "+reservation.Salle.Nom,
            montant = proposal.Prix,
            dateDepense = proposal.DateDebut.ToString("yyyy-MM-dd"),
            statut = proposal.Statut,
            version = reservation.BudgetVersion
        });
}
