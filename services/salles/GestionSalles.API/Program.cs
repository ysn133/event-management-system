
using GestionSalles.API.Data;
using Microsoft.EntityFrameworkCore;
using DotNetEnv;
using System.Text.Json.Serialization;

// Charger les variables du fichier .env
Env.Load();

var builder = WebApplication.CreateBuilder(args);

// Configuration MVC et sérialisation des enums
builder.Services
    .AddControllersWithViews()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(
            new JsonStringEnumConverter()
        );
    });

// Configuration CORS pour React
builder.Services.AddCors(options =>
{
    options.AddPolicy("ReactFrontend", policy =>
    {
        policy.WithOrigins("http://localhost:5173")
              .AllowAnyHeader()
              .AllowAnyMethod();
    });
});

// Configuration OpenAPI
builder.Services.AddOpenApi();

// Récupération des variables du fichier .env
var host = Environment.GetEnvironmentVariable("DB_HOST");
var port = Environment.GetEnvironmentVariable("DB_PORT");
var database = Environment.GetEnvironmentVariable("DB_NAME");
var username = Environment.GetEnvironmentVariable("DB_USER");
var password = Environment.GetEnvironmentVariable("DB_PASSWORD");

// Vérification de la configuration
if (string.IsNullOrWhiteSpace(host) ||
    string.IsNullOrWhiteSpace(port) ||
    string.IsNullOrWhiteSpace(database) ||
    string.IsNullOrWhiteSpace(username) ||
    string.IsNullOrWhiteSpace(password))
{
    throw new InvalidOperationException(
        "Configuration PostgreSQL locale incomplète. Vérifiez le fichier .env."
    );
}

// Connexion PostgreSQL locale
var connectionString =
    $"Host={host};Port={port};Database={database};" +
    $"Username={username};Password={password};SSL Mode=Disable";

// Configuration Entity Framework Core
builder.Services.AddDbContext<ApplicationDbContext>(options =>
    options.UseNpgsql(connectionString)
);

var app = builder.Build();

// Configuration de l'environnement
if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
}

// Middleware HTTP
app.UseHttpsRedirection();
app.UseStaticFiles();
app.UseRouting();
app.UseCors("ReactFrontend");
app.UseAuthorization();

// OpenAPI en développement
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

// Routes API
app.MapControllers();

// Routes MVC
app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}"
);

app.Run();
