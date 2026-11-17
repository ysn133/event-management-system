using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using GestionSalles.API.Security;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Hosting.Server;
using Microsoft.AspNetCore.Hosting.Server.Features;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;

namespace GestionSalles.Tests.Infrastructure;

public class ApiAuthenticationTests
{
    private class AuthHandler : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken token)
        {
            Assert.EndsWith("/api/auth/me",request.RequestUri!.ToString());
            string bearer=request.Headers.Authorization?.Parameter??"";
            return Task.FromResult(bearer switch {
                "valid" => new HttpResponseMessage(HttpStatusCode.OK) {Content=JsonContent.Create(new {id=1,nom="Tester"})},
                "outage" => new HttpResponseMessage(HttpStatusCode.ServiceUnavailable),
                "malformed-identity" => new HttpResponseMessage(HttpStatusCode.OK) {Content=JsonContent.Create(new {id=0})},
                _ => new HttpResponseMessage(HttpStatusCode.Unauthorized)
            });
        }
    }

    [Fact]
    public async Task FallbackPolicyProtectsEveryApiVerbWhileHealthStaysPublic()
    {
        var builder=WebApplication.CreateBuilder();
        builder.WebHost.UseKestrel(options=>options.Listen(IPAddress.Loopback,0));
        builder.Services.AddEventFlowAuthentication();
        builder.Services.AddHttpClient("AuthValidation").ConfigurePrimaryHttpMessageHandler(()=>new AuthHandler());
        await using var app=builder.Build();
        app.UseAuthentication(); app.UseAuthorization();
        app.MapMethods("/api/probe",new[]{"GET","POST","PUT","PATCH","DELETE"},()=>Results.Ok(new {message="allowed"}));
        app.MapGet("/health",()=>Results.Ok()).AllowAnonymous();
        await app.StartAsync();
        string address=app.Services.GetRequiredService<IServer>().Features.Get<IServerAddressesFeature>()!.Addresses.Single();
        using var client=new HttpClient {BaseAddress=new Uri(address)};
        Assert.Equal(HttpStatusCode.OK,(await client.GetAsync("/health")).StatusCode);
        foreach(string method in new[]{"GET","POST","PUT","PATCH","DELETE"})
            Assert.Equal(HttpStatusCode.Unauthorized,(await client.SendAsync(new HttpRequestMessage(new HttpMethod(method),"/api/probe"))).StatusCode);
        foreach(var (token,status) in new[]{("valid",200),("tampered",401),("expired",401),("outage",503),("malformed-identity",503)})
        {
            using var request=new HttpRequestMessage(HttpMethod.Get,"/api/probe");
            request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",token);
            Assert.Equal(status,(int)(await client.SendAsync(request)).StatusCode);
        }
        await app.StopAsync();
    }
}
