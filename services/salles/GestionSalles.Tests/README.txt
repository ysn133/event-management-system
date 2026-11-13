Fichiers de tests unitaires pour GestionSalles.Tests

Copier les dossiers Controllers et Infrastructure dans le projet GestionSalles.Tests.

Prérequis :
- référence de projet vers GestionSalles.API
- Microsoft.EntityFrameworkCore.Sqlite
- xUnit

Exécution :
1. Arrêter GestionSalles.API si son exécutable est encore en cours d'utilisation.
2. Depuis le dossier GestionSalles.Tests, lancer : dotnet test

Limite importante :
Les tests de ReservationsApiController.Create couvrent les validations de dates et le cas où la salle n'existe pas.
Le scénario nominal et la vérification de l'événement ne sont pas inclus ici : le contrôleur exécute directement une requête SQL PostgreSQL sur public.evenements, non compatible avec SQLite en mémoire.
Pour tester ces scénarios sans PostgreSQL, il faudra isoler la vérification d'existence de l'événement dans une dépendance injectable. Sinon, utiliser une base PostgreSQL dédiée aux tests.
