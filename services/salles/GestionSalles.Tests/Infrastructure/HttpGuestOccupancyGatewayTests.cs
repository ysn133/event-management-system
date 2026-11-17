using System.Net;
using System.Net.Http.Json;
using GestionSalles.API.Integration;
using Microsoft.Extensions.Configuration;
namespace GestionSalles.Tests.Infrastructure;

public class HttpGuestOccupancyGatewayTests
{
    private class Handler(Func<HttpRequestMessage,HttpResponseMessage> send) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken token) => Task.FromResult(send(request));
    }
    private static IConfiguration Config() => new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string,string?> {
        ["INVITES_SERVICE_URL"]="http://guests.test"
    }).Build();
    [Fact]
    public async Task ForwardsJwtAndUsesCountOnlyEndpoint()
    {
        using var http=new HttpClient(new Handler(request=> {
            Assert.Equal("http://guests.test/api/v1/evenements/20/occupation",request.RequestUri!.ToString());
            Assert.Equal("Bearer caller-token",request.Headers.Authorization!.ToString());
            return new(HttpStatusCode.OK) {Content=JsonContent.Create(new {evenement_id=20,places_occupees=80})};
        }));
        Assert.Equal(80,await new HttpGuestOccupancyGateway(http,Config()).OccupiedSeats(20,"Bearer caller-token"));
    }
    [Theory]
    [InlineData("{}")] [InlineData("{\"evenement_id\":21,\"places_occupees\":2}")]
    [InlineData("{\"evenement_id\":20,\"places_occupees\":-1}")] [InlineData("not-json")]
    public async Task MissingOrInvalidCountNeverMeansZeroGuests(string body)
    {
        using var http=new HttpClient(new Handler(_=>new(HttpStatusCode.OK) {Content=new StringContent(body)}));
        var error=await Assert.ThrowsAsync<RemoteServiceFailure>(()=>new HttpGuestOccupancyGateway(http,Config()).OccupiedSeats(20,"Bearer token"));
        Assert.Equal(503,error.Status);
    }
    [Theory]
    [InlineData(401,401)] [InlineData(403,403)] [InlineData(404,503)] [InlineData(500,503)]
    public async Task RefusesChangesWhenCountCannotBeVerified(int status,int expected)
    {
        using var http=new HttpClient(new Handler(_=>new((HttpStatusCode)status)));
        var error=await Assert.ThrowsAsync<RemoteServiceFailure>(()=>new HttpGuestOccupancyGateway(http,Config()).OccupiedSeats(20,"Bearer token"));
        Assert.Equal(expected,error.Status);
    }
}
