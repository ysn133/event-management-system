FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /build
COPY services/salles/GestionSalles.API/GestionSalles.API.csproj ./
RUN dotnet restore
COPY services/salles/GestionSalles.API/ ./
RUN dotnet publish -c Release --no-restore -o /out /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0
USER root
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /out ./
ENV ASPNETCORE_URLS=http://+:5242 ASPNETCORE_ENVIRONMENT=Production
USER app
EXPOSE 5242
ENTRYPOINT ["dotnet", "GestionSalles.API.dll"]
