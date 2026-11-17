using System.Net;
using System.Net.Http.Headers;
using System.Security.Claims;
using System.Text.Encodings.Web;
using System.Text.Json;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;

namespace GestionSalles.API.Security;

/// Auth owns JWT validation. No API request is trusted merely for carrying a Bearer header.
public class AuthServiceAuthenticationHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder,
    IHttpClientFactory clients,
    IConfiguration config) : AuthenticationHandler<AuthenticationSchemeOptions>(options,logger,encoder)
{
    public const string SchemeName="EventFlowBearer";
    private bool _unavailable;

    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        string raw=Request.Headers.Authorization.ToString();
        if (string.IsNullOrWhiteSpace(raw)) return AuthenticateResult.NoResult();
        if (!AuthenticationHeaderValue.TryParse(raw,out var header)
            || !"Bearer".Equals(header.Scheme,StringComparison.OrdinalIgnoreCase)
            || string.IsNullOrWhiteSpace(header.Parameter)) return AuthenticateResult.Fail("Invalid authorization header");
        try
        {
            string url=(config["AUTH_SERVICE_URL"]??"http://localhost:5005").TrimEnd('/');
            using var request=new HttpRequestMessage(HttpMethod.Get,url+"/api/auth/me");
            request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",header.Parameter);
            using var timeout=CancellationTokenSource.CreateLinkedTokenSource(Context.RequestAborted);
            timeout.CancelAfter(TimeSpan.FromSeconds(8));
            using var response=await clients.CreateClient("AuthValidation").SendAsync(request,timeout.Token);
            if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
                return AuthenticateResult.Fail("Invalid or expired session");
            if (!response.IsSuccessStatusCode) throw new HttpRequestException("Auth unavailable");
            using var json=JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
            if (!json.RootElement.TryGetProperty("id",out var value) || !value.TryGetInt64(out long id) || id<=0)
                throw new JsonException("Invalid identity response");
            var identity=new ClaimsIdentity(new[]{new Claim(ClaimTypes.NameIdentifier,id.ToString())},SchemeName);
            return AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity),SchemeName));
        }
        catch(Exception error) when(error is HttpRequestException or OperationCanceledException or JsonException or InvalidOperationException)
        {
            _unavailable=true;
            return AuthenticateResult.Fail("Authentication unavailable");
        }
    }

    protected override async Task HandleChallengeAsync(AuthenticationProperties properties)
    {
        Response.StatusCode=_unavailable?503:401;
        if (!_unavailable) Response.Headers.WWWAuthenticate="Bearer";
        await Response.WriteAsJsonAsync(new {message=_unavailable
            ?"Authentification temporairement indisponible."
            :"Authentification requise ou session expirée."});
    }
}
