using System.Net;
using System.Net.Http.Json;
using GestionSalles.API.Integration;
using GestionSalles.API.Models;
using GestionSalles.API.Services;
using Microsoft.Extensions.Configuration;
namespace GestionSalles.Tests.Infrastructure;

public class HttpRoomBudgetGatewayTests
{
    private class Handler(Func<HttpRequestMessage,Task<HttpResponseMessage>> send) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken cancellationToken) => send(request);
    }
    private static IConfiguration Config() => new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string,string?> {
        ["AUTH_SERVICE_URL"]="http://auth.test",["BUDGET_SERVICE_URL"]="http://budget.test",["ROOM_BUDGET_SERVICE_KEY"]="room-private-key"
    }).Build();
    private static ReservationSalle Reservation() => new() { Id=7,EvenementId=20,BudgetVersion=3,Salle=new Salle { Nom="Salle Atlas" } };
    private static RoomChange Proposal() => new(new DateTime(2027,1,10,9,0,0,DateTimeKind.Utc),new DateTime(2027,1,10,18,0,0,DateTimeKind.Utc),700,"EN_ATTENTE");
    [Fact]
    public async Task ApprovalForwardsJwtPrivateKeyAndVersion()
    {
        using var http=new HttpClient(new Handler(async request => {
            Assert.Equal(HttpMethod.Put,request.Method);
            Assert.Equal("http://budget.test/api/budgets/evenement/20/reservations-salle/7",request.RequestUri!.ToString());
            Assert.Equal("Bearer caller-token",request.Headers.Authorization!.ToString());
            Assert.Equal("room-private-key",request.Headers.GetValues("X-Service-Key").Single());
            var body=await request.Content!.ReadFromJsonAsync<System.Text.Json.JsonElement>();
            Assert.Equal(700,body.GetProperty("montant").GetDecimal());
            Assert.Equal(3,body.GetProperty("version").GetInt64());
            return new HttpResponseMessage(HttpStatusCode.OK);
        }));
        await new HttpRoomBudgetGateway(http,Config()).Approve(Reservation(),Proposal(),"Bearer caller-token");
    }
    [Fact]
    public async Task UserValidationDoesNotSendTheServiceKey()
    {
        using var http=new HttpClient(new Handler(request => {
            Assert.Equal("http://auth.test/api/auth/me",request.RequestUri!.ToString());
            Assert.False(request.Headers.Contains("X-Service-Key"));
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK));
        }));
        await new HttpRoomBudgetGateway(http,Config()).ValidateUser("Bearer caller-token");
    }
    [Fact]
    public async Task MissingJwtIsRejectedBeforeAnyHttpRequest()
    {
        using var http=new HttpClient(new Handler(_=>throw new Exception("HTTP must not be called")));
        var error=await Assert.ThrowsAsync<RemoteServiceFailure>(()=>new HttpRoomBudgetGateway(http,Config()).ValidateUser(""));
        Assert.Equal(401,error.Status);
    }
    [Fact]
    public async Task BudgetDenialAndNetworkFailureHaveDifferentRecoveryStates()
    {
        using var denied=new HttpClient(new Handler(_=>Task.FromResult(new HttpResponseMessage(HttpStatusCode.Conflict) { Content=JsonContent.Create(new {message="Solde insuffisant"}) })));
        var refusal=await Assert.ThrowsAsync<RemoteServiceFailure>(()=>new HttpRoomBudgetGateway(denied,Config()).Approve(Reservation(),Proposal(),"Bearer token"));
        Assert.Equal(409,refusal.Status); Assert.True(refusal.Definitive);
        using var unavailable=new HttpClient(new Handler(_=>throw new HttpRequestException("Unavailable")));
        var uncertain=await Assert.ThrowsAsync<RemoteServiceFailure>(()=>new HttpRoomBudgetGateway(unavailable,Config()).Approve(Reservation(),Proposal(),"Bearer token"));
        Assert.Equal(503,uncertain.Status); Assert.False(uncertain.Definitive);
    }
}
