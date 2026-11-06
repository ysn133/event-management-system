
using GestionSalles.API.Data;
using Microsoft.EntityFrameworkCore;
using DotNetEnv;

// 1. Charger les variables du fichier .env
Env.Load();

// 2. Créer le builder
var builder = WebApplication.CreateBuilder(args);

// 3. Enregistrement MVC
builder.Services.AddControllersWithViews();

// 4. Configuration CORS pour React
builder.Services.AddCors(options =>
{
    options.AddPolicy("ReactFrontend", policy =>
    {
        policy.WithOrigins("http://localhost:5173")
              .AllowAnyHeader()
              .AllowAnyMethod();
    });
});

// 5. Configuration OpenAPI
builder.Services.AddOpenApi();

// 6. Récupération des variables d'environnement
var host = Environment.GetEnvironmentVariable("DB_HOST");
var port = Environment.GetEnvironmentVariable("DB_PORT");
var database = Environment.GetEnvironmentVariable("DB_NAME");
var username = Environment.GetEnvironmentVariable("DB_USER");
var password = Environment.GetEnvironmentVariable("DB_PASSWORD");

// 7. Vérification de la configuration
if (string.IsNullOrWhiteSpace(host) ||
    string.IsNullOrWhiteSpace(port) ||
    string.IsNullOrWhiteSpace(database) ||
    string.IsNullOrWhiteSpace(username) ||
    string.IsNullOrWhiteSpace(password))
{
    throw new InvalidOperationException(
        "Configuration Supabase incomplète. Vérifiez votre fichier .env."
    );
}

// 8. Construction de la chaîne de connexion PostgreSQL
var connectionString =
    $"Host={host};Port={port};Database={database};" +
    $"Username={username};Password={password};SSL Mode=Require";

// 9. Configuration Entity Framework Core
builder.Services.AddDbContext<ApplicationDbContext>(options =>
    options.UseNpgsql(connectionString)
);

// 10. Construire l'application
var app = builder.Build();

// 11. Configuration de l'environnement
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
}

// 12. Middleware HTTP
app.UseHttpsRedirection();
app.UseStaticFiles();
app.UseRouting();
app.UseCors("ReactFrontend");
app.UseAuthorization();

// 13. Exposition OpenAPI en développement
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

// 14. Routes API REST
app.MapControllers();

// 15. Routes MVC et Razor Views
app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}"
);

// 16. Démarrage
app.Run();
