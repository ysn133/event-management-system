using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authorization;

namespace GestionSalles.API.Security;

public static class EventFlowAuthenticationExtensions
{
    public static IServiceCollection AddEventFlowAuthentication(this IServiceCollection services)
    {
        services.AddHttpClient("AuthValidation");
        services.AddAuthentication(AuthServiceAuthenticationHandler.SchemeName)
            .AddScheme<AuthenticationSchemeOptions,AuthServiceAuthenticationHandler>(AuthServiceAuthenticationHandler.SchemeName,_=>{});
        services.AddAuthorization(options => options.FallbackPolicy=new AuthorizationPolicyBuilder()
            .RequireAuthenticatedUser().Build());
        return services;
    }
}
