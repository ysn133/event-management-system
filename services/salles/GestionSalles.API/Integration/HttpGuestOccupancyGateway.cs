using System.Text.Json;

namespace GestionSalles.API.Integration;

public class HttpGuestOccupancyGateway(HttpClient http, IConfiguration config) : IGuestOccupancyGateway
{
    public async Task<long> OccupiedSeats(long eventId,string authorization)
    {
        if (!authorization.StartsWith("Bearer ",StringComparison.OrdinalIgnoreCase))
            throw new RemoteServiceFailure(401,"Connectez-vous pour modifier la capacité d’un événement.");
        var url=(config["INVITES_SERVICE_URL"]??"http://localhost:5003").TrimEnd('/');
        using var request=new HttpRequestMessage(HttpMethod.Get,$"{url}/api/v1/evenements/{eventId}/occupation");
        request.Headers.TryAddWithoutValidation("Authorization",authorization);
        try
        {
            using var timeout=new CancellationTokenSource(TimeSpan.FromSeconds(8));
            using var response=await http.SendAsync(request,timeout.Token);
            if ((int)response.StatusCode is 401 or 403)
                throw new RemoteServiceFailure((int)response.StatusCode,"Le service Invités a refusé l’accès. Reconnectez-vous.");
            if (!response.IsSuccessStatusCode) throw new HttpRequestException("Guest count unavailable");
            using var json=JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
            if (!json.RootElement.TryGetProperty("evenement_id",out var id) || !id.TryGetInt64(out var actualId) || actualId!=eventId
                || !json.RootElement.TryGetProperty("places_occupees",out var count) || !count.TryGetInt64(out var occupied) || occupied<0)
                throw new JsonException("Invalid guest count");
            return occupied;
        }
        catch (Exception error) when (error is HttpRequestException or OperationCanceledException or JsonException or InvalidOperationException)
        {
            throw new RemoteServiceFailure(503,"Impossible de vérifier les invités : aucune réduction de capacité n’a été validée.");
        }
    }
}
