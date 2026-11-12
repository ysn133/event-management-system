# Module « Gestion des invités et des invitations »

Backend REST (FastAPI + PostgreSQL) du module **Invités et invitations** de l'application de gestion
des événements. Responsable du module : Abdessadek Tagarte.

> **État de la livraison — à lire en premier.** Le code est complet, mais il n'a pas pu être exécuté
> de bout en bout dans l'environnement où il a été écrit (FastAPI, SQLAlchemy, psycopg et
> httpx n'y sont pas installés et le réseau est coupé). Ce qui a été **réellement exécuté** et ce qui
> reste **à lancer chez vous** est détaillé dans [Rapport d'exécution des tests](#rapport-dexécution-des-tests).
> Aucun test d'intégration n'a été exécuté.

## Sommaire

1. [Présentation et objectifs](#présentation-et-objectifs)
2. [Technologies](#technologies)
3. [Architecture](#architecture)
4. [Décisions de conception](#décisions-de-conception)
5. [Prérequis et installation](#prérequis-et-installation)
6. [Configuration (`.env`)](#configuration-env)
7. [Base de données](#base-de-données)
8. [Démarrage et Swagger](#démarrage-et-swagger)
9. [Endpoints](#endpoints)
10. [Exemples de requêtes et réponses](#exemples-de-requêtes-et-réponses)
11. [Tests](#tests)
12. [Intégration avec le projet principal](#intégration-avec-le-projet-principal)
13. [Récapitulatif des fonctionnalités](#récapitulatif-des-fonctionnalités)
14. [Limites connues](#limites-connues)

---

## Présentation et objectifs

Le module permet à un organisateur de :

- gérer des **invités** (ajout, liste, consultation, modification, suppression, recherche par nom,
  e-mail ou téléphone) avec validation stricte des données ;
- **associer** des invités à des événements existants (sans doublon, un invité pouvant participer à
  plusieurs événements) ;
- **envoyer** les invitations et **suivre les réponses** (`EN_ATTENTE`, `ACCEPTEE`, `REFUSEE`) ;
- consulter les **statistiques** d'un événement (totaux et pourcentages).

Il est un **service de plus** de l'architecture de l'équipe : il partage la base PostgreSQL commune
et **interroge le service Événements en HTTP** (voir [Intégration](#intégration-avec-le-projet-principal)).
Il **ne gère pas** les événements : il ne lit pas leur table, il demande au service qui en est propriétaire.

## Technologies

Python 3.12+, FastAPI, PostgreSQL, SQLAlchemy 2.x, Pydantic v2, Pytest, HTTPX,
python-dotenv, Uvicorn. Une seule dépendance hors liste imposée : le pilote **psycopg 3**, nécessaire
pour que SQLAlchemy parle à PostgreSQL. La validation des e-mails est faite sans bibliothèque
externe (voir [Décisions](#décisions-de-conception)).

## Architecture

```text
module_invites/
├── app/
│   ├── main.py                  # create_app() : CORS, routers, erreurs, /health
│   ├── core/                    # config.py, enums.py, exceptions.py, security.py
│   ├── database/                # base.py (Base), connection.py (engine, sessions)
│   ├── clients/                 # evenement_client.py : appelle le service Événements (HTTP)
│   ├── models/                  # invite.py, invitation.py
│   ├── schemas/                 # common.py, invite.py, invitation.py, association.py, statistiques.py
│   ├── repositories/            # invite_, invitation_repository.py, errors.py
│   ├── services/                # invite_, association_, invitation_, statistiques_, notification_service.py, base.py
│   ├── api/                     # dependencies.py, error_handlers.py, responses.py, routes/
│   └── tests/                   # unit/, integration/, fixtures/
├── Dockerfile, .dockerignore
├── deploy/                      # compose-invites-service.yaml : à ajouter au compose.yaml de l'équipe
├── sql/                         # ameliorations_requises.sql, ameliorations_optionnelles.sql
├── docker-compose.yml (base de test), requirements.txt, .env.example, pytest.ini
```

| Couche | Rôle | Ne fait jamais |
|---|---|---|
| **api** (routes) | Lit les paramètres, appelle un service, choisit le code HTTP | Logique métier, SQL, commit |
| **services** | Règles métier, transactions (commit/rollback), lèvent des exceptions métier | SQL direct, `HTTPException` |
| **repositories** | Requêtes SQLAlchemy paramétrées, `flush` | Commit, règles métier |
| **clients** | Appels HTTP aux autres services de l'équipe | Écrire dans leurs tables |
| **core** | Configuration, exceptions, point d'intégration de la sécurité | Dépendre des routes |

Changements par rapport à la structure proposée dans le cahier des charges :

- `association_service.py` et `routes/associations.py` : l'association est séparée de l'envoi.
- `clients/evenement_client.py` : **seul** point d'accès aux événements (appel HTTP au service Événements).
- `core/security.py`, `core/enums.py`, `api/error_handlers.py`, `api/responses.py`, `services/base.py`,
  `repositories/errors.py` : sécurité branchable, énumérations sans dépendance, gestion centralisée des erreurs.
- `tests/` découpé en `unit/` (sans base) et `integration/` (PostgreSQL réel).
- `sql/` : améliorations de schéma, séparées du code.

## Décisions de conception

| Sujet | Décision |
|---|---|
| Association et invitation | Une seule table, `invitations`. Associer crée une ligne `EN_ATTENTE`, `date_envoi` nulle. **L'association préalable est obligatoire** pour envoyer. |
| Réponse vs envoi | `statut` = réponse de l'invité (3 valeurs strictes). `etat_envoi` (`ENVOYEE` / `NON_ENVOYEE`) est **calculé** à partir de `date_envoi`, **jamais stocké**. |
| Échec d'envoi | Non conservé en base (schéma inchangé). Il est renvoyé dans la réponse (502) et journalisé. |
| `date_envoi` | Renseignée **uniquement** si le service d'envoi confirme l'envoi. |
| Envoi simulé | Activation **explicite** (`NOTIFICATION_BACKEND=simulated`), interdit en production. Les réponses portent `"simulation": true` : aucun message réel n'est envoyé. |
| Événements | Le module **n'a pas de modèle ni de table « événement »** : il appelle `GET /api/evenements/{id}` du service Événements. 404 du service → 404 `EVENEMENT_INTROUVABLE` ; service arrêté ou 5xx → **503** `SERVICE_EVENEMENTS_INDISPONIBLE` ; réponse invalide → **502**. |
| Migrations | **Pas d'Alembic** : les tables viennent des scripts de l'équipe (`database/init`), comme pour les autres services. |
| Schéma de référence | Le schéma **réel de la base commune** de l'équipe (`database/init/001_schema.sql`, voir [Intégration](#intégration-avec-le-projet-principal)), qui diffère du DDL du cahier des charges. **Aucune modification n'est nécessaire** pour que le module fonctionne. Les colonnes `envoi_statut` / `envoi_erreur` restent dans un SQL optionnel et ne sont pas utilisées. |
| Doublons d'e-mails | Autorisés. Doublons d'associations : **interdits** (contrainte `UNIQUE` + 409). |
| Suppression d'invité | 409 s'il a des invitations. Dans la base commune la clé étrangère est `ON DELETE CASCADE` : elle n'empêcherait rien et effacerait les invitations en silence, donc le `DELETE` est **conditionnel** (`NOT EXISTS`). |
| Suppression d'association | 409 si l'invitation est envoyée ou a reçu une réponse. Le `DELETE` est conditionnel, donc sûr même avec des requêtes concurrentes. |
| Événement annulé ou non planifié | Aucune restriction tant que les règles du module Événements ne sont pas connues. |
| Modification d'un invité | `PUT` complet. Pas de `PATCH` partiel dans la version 1. |
| Changement de statut | Toute transition entre les 3 statuts est permise (correction d'erreur, réponse reçue par téléphone). |
| Codes HTTP | 200, 201, 204, 400, 404, 409, 422, 500, plus **401/403** (sécurité), **502** (échec du fournisseur d'envoi ou réponse invalide du service Événements) et **503** (aucun fournisseur d'envoi, ou service Événements injoignable). |
| Envoi d'invitation | `POST …/invitations` renvoie **200** (et non 201) : il ne crée aucune ressource, il déclenche l'envoi d'une association existante. |
| Réponses d'association | Le champ `statut_invitation` (et non `statut`) désigne la réponse de l'invité, pour ne pas le confondre avec le `statut` de l'événement. |
| E-mail | Validation par expression régulière (ASCII, 254 caractères max). Les adresses internationalisées (caractères non ASCII) sont refusées. |
| Longueurs | `nom` ≤ 150 caractères (colonne `VARCHAR(150)`), e-mail ≤ 254. |
| Téléphone | 6 à 20 caractères parmi chiffres, espaces, `+ - . ( )`, avec 6 à 15 chiffres. |
| Authentification | Non fournie : seul un **point d'intégration** existe (voir [Sécurité](#sécurité)). |

## Prérequis et installation

- Python 3.12 ou plus, ou Docker ; la base PostgreSQL de l'équipe ; **le service Événements lancé** (port 5001).

```bash
python -m venv .venv
source .venv/bin/activate            # Windows : .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env                 # puis adaptez les valeurs
```

## Configuration (`.env`)

| Variable | Rôle | Défaut |
|---|---|---|
| `APP_ENV` | `development`, `test` ou `production` | `development` |
| `DEBUG` | Journaux détaillés (aucune trace n'est jamais renvoyée au client) | `false` |
| `DATABASE_URL` | URL PostgreSQL de la base commune de l'équipe, port 5433 par défaut (**obligatoire**) | — |
| `EVENEMENTS_SERVICE_URL` | URL du service Événements de l'équipe | `http://localhost:5001` |
| `EVENEMENTS_TIMEOUT_SECONDS` | Délai maximal d'attente de ce service | `5` |
| `TEST_DATABASE_URL` | Base **dédiée** aux tests (port 5434), son nom doit contenir « test » | — |
| `CORS_ORIGINS` | Origines autorisées, séparées par des virgules (vide = aucune) | vide |
| `AUTH_ENABLED` | Active le contrôle d'identité | `false` |
| `ORGANISER_CHECK_ENABLED` | Seul l'organisateur de l'événement peut agir dessus | `false` |
| `NOTIFICATION_BACKEND` | `none` (503 à l'envoi) ou `simulated` | `none` |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Utilisées uniquement par `docker-compose.yml` (base de test) | — |

Règles imposées au démarrage (l'application **refuse de démarrer** sinon) :

- `APP_ENV=production` exige `AUTH_ENABLED=true` ;
- `APP_ENV=production` interdit `NOTIFICATION_BACKEND=simulated` ;
- `ORGANISER_CHECK_ENABLED=true` exige `AUTH_ENABLED=true`.

Aucun secret n'est écrit dans le code. `.env` est ignoré par Git ; ne commitez que `.env.example`.

### Sécurité

| Contexte | Comportement |
|---|---|
| Développement local, `AUTH_ENABLED=false` | Aucune identité demandée ; **avertissement** dans les journaux au démarrage. |
| Production | `AUTH_ENABLED=true` **obligatoire**. |
| `AUTH_ENABLED=true` sans fournisseur d'identité branché | Accès **refusé** (401) sur toutes les routes `/api/v1`, jamais accordé automatiquement (`/health` reste public). |
| `AUTH_ENABLED=true` avec fournisseur | L'identité retournée par le fournisseur est utilisée. |

**L'authentification n'est pas intégrée** : ce module ne contient aucun système d'identité. Le contrôle
« l'utilisateur est l'organisateur de l'événement » (`ORGANISER_CHECK_ENABLED`) est codé mais
désactivé ; il doit être activé lors de l'intégration avec le module d'authentification principal.

## Base de données

Le module utilise la **base commune** de l'équipe (PostgreSQL 18). Ses tables `invites` et
`invitations` sont **déjà créées** par `database/init/001_schema.sql` du dépôt de l'équipe, avec
l'unicité `uk_invitations_invite_evenement` et l'index `idx_invitations_evenement_id`. Le module **ne crée
ni ne modifie aucune table** : il n'y a donc **pas d'Alembic**, comme dans les autres services.

Si une évolution du schéma est nécessaire, on modifie `database/init/001_schema.sql` dans le dépôt de
l'équipe (ou on ajoute un script), comme pour les autres modules.

| Élément | Cahier des charges | Base commune |
|---|---|---|
| Identifiants | `GENERATED ALWAYS` | `GENERATED BY DEFAULT` |
| Unicité (invite, événement) | absente | **présente** (`uk_invitations_invite_evenement`) |
| Contrôle de `statut` | `CHECK` | **absent** (validé par l'application) |
| Contact d'un invité | validé par l'application | `CHECK` (e-mail ou téléphone) en plus |
| `invites.nom` | `varchar` | `varchar(150)` |
| FK `invitations.invite_id` | sans `ON DELETE` | **`ON DELETE CASCADE`** |

La clé étrangère `invitations.evenement_id → evenements(id)` (`ON DELETE RESTRICT`) existe toujours en base :
un événement ayant des invitations ne peut pas être supprimé par le service Événements.

### Scripts SQL optionnels

| Fichier | Rôle |
|---|---|
| `sql/ameliorations_recommandees.sql` | Ajoute `CHECK (statut IN ('EN_ATTENTE','ACCEPTEE','REFUSEE'))`, **absent de la base commune**. Non requis (l'application valide déjà), mais protège contre une écriture directe. Schéma partagé : **à faire valider par l'équipe**. |
| `sql/ameliorations_optionnelles.sql` | Colonnes `envoi_statut` / `envoi_erreur`. **L'application ne les utilise pas.** |
| `sql/ameliorations_requises.sql` | Pour un schéma issu du cahier des charges **sans** unicité. **Sur la base commune il ne fait rien** (il l'indique). |

Pour repérer des doublons d'associations existants :

```sql
SELECT invite_id, evenement_id, count(*) FROM public.invitations GROUP BY 1, 2 HAVING count(*) > 1;
```

## Démarrage et Swagger

### Avec Docker (recommandé : démarrage avec le reste du projet)

Le module a un `Dockerfile`. Pour qu'il démarre avec la base de l'équipe, **ajoutez le service**
décrit dans `deploy/compose-invites-service.yaml` au `compose.yaml` de la racine du dépôt de l'équipe
(section `services:`), puis, depuis la racine du dépôt :

```bash
docker compose up -d --build invites-service     # démarre aussi postgres s'il est arrêté
```

Le conteneur lit sa configuration dans les variables d'environnement du compose (aucun `.env` n'est
copié dans l'image). Le service Événements (Spring Boot) n'ayant pas encore de Dockerfile, il doit
tourner sur votre machine (port 5001) : le conteneur l'atteint via `host.docker.internal`.
Quand il aura son propre service Docker, changez `EVENEMENTS_SERVICE_URL` en
`http://evenement-service:5001`.

- Swagger UI : <http://localhost:5003/docs> — Santé : `GET /health`

### Sans Docker (développement)

```bash
# Le service Événements doit tourner (http://localhost:5001), ainsi que la base commune.
uvicorn app.main:app --reload --port 5003
```

- Swagger UI : <http://localhost:5003/docs> — ReDoc : <http://localhost:5003/redoc>

Si le service Événements est arrêté, les routes qui en ont besoin répondent **503**
`SERVICE_EVENEMENTS_INDISPONIBLE` (les routes sur les invités seuls continuent de fonctionner).

## Endpoints

Préfixe `/api/v1`. Pagination : `limit` (1 à 100, défaut 20) et `offset` (≥ 0). Réponse de liste :
`{"items": [...], "total": n, "limit": n, "offset": n}`. Les identifiants sont des entiers > 0, sinon 422.

### Invités

| Méthode et chemin | Succès | Erreurs |
|---|---|---|
| `POST /invites` | 201 | 422 |
| `GET /invites` | 200 | 422 |
| `GET /invites/search?nom=&email=&telephone=` | 200 | **400** si aucun critère, 422 |
| `GET /invites/{invite_id}` | 200 | 404 |
| `PUT /invites/{invite_id}` | 200 | 404, 422 |
| `DELETE /invites/{invite_id}` | 204 | 404, **409** si des invitations existent |

La recherche est partielle, insensible à la casse ; les critères fournis se combinent (ET) ; `%` et `_`
saisis sont traités comme du texte.

### Associations

| Méthode et chemin | Succès | Erreurs |
|---|---|---|
| `POST /evenements/{evenement_id}/invites/{invite_id}` | 201 | 404 (invité ou événement), **409** doublon |
| `GET /evenements/{evenement_id}/invites?statut=` | 200 | 404 |
| `GET /invites/{invite_id}/evenements?statut=` | 200 | 404 |
| `DELETE /evenements/{evenement_id}/invites/{invite_id}` | 204 | 404, **409** si envoyée ou répondue |

### Invitations

| Méthode et chemin | Succès | Erreurs |
|---|---|---|
| `POST /evenements/{evenement_id}/invitations` | **200** (envoi confirmé) | 404, **409** (non associé, déjà envoyée), 422 (contact manquant pour le canal), **502** (échec du fournisseur), **503** (aucun fournisseur) |
| `GET /invitations/{invitation_id}` | 200 | 404 |
| `GET /evenements/{evenement_id}/invitations?statut=&envoyee=` | 200 | 404 |
| `GET /invites/{invite_id}/invitations?statut=` | 200 | 404 |
| `PATCH /invitations/{invitation_id}/statut` | 200 | 404, **422** statut invalide |

### Statistiques

| Méthode et chemin | Succès | Erreurs |
|---|---|---|
| `GET /evenements/{evenement_id}/statistiques-invitations` | 200 | 404 |

Calcul en **une seule requête SQL** (`COUNT … FILTER`). Pourcentages sur le total, arrondis à 2
décimales, `0` si l'événement n'a aucune invitation (pas de division par zéro).

### Format unique des erreurs

```json
{ "error": { "code": "ASSOCIATION_EXISTANTE", "message": "…", "details": [] } }
```

Aucun détail technique (requête SQL, URL de connexion, trace) n'est renvoyé : il reste dans les
journaux du serveur. Une erreur de base de données répond `500` avec un message générique.

## Exemples de requêtes et réponses

Les valeurs ci-dessous sont illustratives (les identifiants et dates dépendent de vos données).

**Créer un invité** — `POST /api/v1/invites`

```json
{ "nom": "Amine Tagarte", "email": "amine@example.com", "telephone": "+212 6 12 34 56 78" }
```

`201` :

```json
{ "id": 1, "nom": "Amine Tagarte", "email": "amine@example.com", "telephone": "+212 6 12 34 56 78" }
```

**E-mail invalide** — `422` :

```json
{
  "error": {
    "code": "DONNEES_INVALIDES",
    "message": "Les données envoyées sont invalides.",
    "details": [
      { "source": "body", "champ": "email", "message": "Format d'adresse e-mail invalide.", "type": "value_error" }
    ]
  }
}
```

**Associer** — `POST /api/v1/evenements/1/invites/1` → `201` :

```json
{ "id": 1, "invite_id": 1, "evenement_id": 1, "statut": "EN_ATTENTE", "date_envoi": null, "etat_envoi": "NON_ENVOYEE" }
```

Même requête une seconde fois → `409` :

```json
{ "error": { "code": "ASSOCIATION_EXISTANTE", "message": "L'invité 1 est déjà associé à l'événement 1." } }
```

**Envoyer l'invitation** — `POST /api/v1/evenements/1/invitations`

```json
{ "invite_id": 1, "canal": "EMAIL" }
```

`200` avec l'envoi **simulé** (`NOTIFICATION_BACKEND=simulated`) : aucun message réel n'est parti.

```json
{
  "invitation": {
    "id": 1, "invite_id": 1, "evenement_id": 1, "statut": "EN_ATTENTE",
    "date_envoi": "2026-11-08T14:30:00Z", "etat_envoi": "ENVOYEE"
  },
  "envoi": { "confirme": true, "canal": "EMAIL", "fournisseur": "simulation", "simulation": true }
}
```

Avec `NOTIFICATION_BACKEND=none` → `503` et `date_envoi` reste nulle :

```json
{ "error": { "code": "FOURNISSEUR_ENVOI_NON_CONFIGURE", "message": "Aucun fournisseur d'envoi (e-mail/SMS) n'est configuré : l'invitation n'a pas été envoyée." } }
```

**Enregistrer une réponse** — `PATCH /api/v1/invitations/1/statut` avec `{ "statut": "ACCEPTEE" }` → `200`
(`statut` valant `ACCEPTEE`). Une valeur hors liste (par exemple `"PEUT_ETRE"`) → `422`.

**Invités d'un événement** — `GET /api/v1/evenements/1/invites` → `200` :

```json
{
  "items": [
    {
      "id": 1, "nom": "Amine Tagarte", "email": "amine@example.com", "telephone": "+212 6 12 34 56 78",
      "invitation_id": 1, "statut_invitation": "ACCEPTEE", "date_envoi": "2026-11-08T14:30:00Z", "etat_envoi": "ENVOYEE"
    }
  ],
  "total": 1, "limit": 20, "offset": 0
}
```

**Statistiques** — `GET /api/v1/evenements/1/statistiques-invitations` → `200` :

```json
{ "evenement_id": 1, "total": 4, "en_attente": 1, "acceptees": 2, "refusees": 1,
  "pourcentage_acceptation": 50.0, "pourcentage_refus": 25.0 }
```

## Tests

Séparés en tests **unitaires** (`app/tests/unit`, aucune base : dépôts factices) et tests
d'**intégration** (`app/tests/integration`, PostgreSQL réel avec la **copie du schéma de l'équipe**,
application FastAPI réelle). Le service Événements n'est **pas** lancé pendant les tests : un double
(`EvenementsSimules`) lit la table `evenements` de la base de test. Le vrai client HTTP est testé à
part dans `test_evenement_client.py`, contre un petit serveur HTTP local.

```bash
pytest                      # tout ce qui peut tourner
pytest -m unit              # unitaires seulement
pytest -m integration       # intégration seulement (PostgreSQL requis)
```

Lancer les tests d'intégration :

```bash
docker compose up -d db_test     # PostgreSQL 18 jetable, port 5434 (5433 = base de l'équipe)
export TEST_DATABASE_URL="postgresql://<POSTGRES_USER>:<POSTGRES_PASSWORD>@localhost:5434/invites_test"
pytest -m integration -v
```

Garde-fou : le nom de la base de `TEST_DATABASE_URL` **doit contenir « test »**, sinon les tests
s'arrêtent (les tables y sont supprimées puis vidées). Les tests n'utilisent **jamais** `DATABASE_URL`.
Si PostgreSQL ou une dépendance manque, les tests d'intégration sont **ignorés (SKIP) avec la raison
affichée**, jamais comptés comme réussis.

### Rapport d'exécution des tests

Session de livraison : Python 3.13, PostgreSQL 16 disponible, mais **sans** FastAPI, SQLAlchemy,
psycopg ni httpx, et sans accès réseau pour les installer.

| Catégorie | Résultat |
|---|---|
| Tests unitaires (schémas, services avec faux dépôts, classification des erreurs PostgreSQL, configuration, sécurité, envoi simulé, statistiques, **client du service Événements**) | **Exécutés : 194 réussis, 0 échec** |
| `test_error_handlers.py` (unitaire, nécessite FastAPI + SQLAlchemy) | **Non exécuté** (ignoré : `fastapi` absent) |
| Tests d'intégration (API, PostgreSQL, schéma commun, concurrence, erreurs de base) | **Écrits, NON exécutés** (ignorés : modules absents). |

Le client du service Événements a été testé contre un **vrai serveur HTTP local** (200, 404, 400, 500,
service arrêté, JSON invalide, champ manquant). Il n'a **pas** été essayé contre le vrai service Java
(non démarré ici) : ses noms de champs ont été comparés au code source `EvenementResponse` et au contrat
`docs/api-contracts/evenement.openapi.yaml` de l'équipe.

Vérifications **réellement exécutées** sur PostgreSQL 16 avec `psql` (sans passer par Python) :

- les scripts `001_schema.sql` et `002_seed.sql` de l'équipe s'appliquent ;
- `sql/ameliorations_recommandees.sql` ajoute le `CHECK` sur `statut` (idempotent) ;
- les messages d'erreur exacts de PostgreSQL (doublon, invité absent, événement absent) servent de
  données aux tests de `classer_erreur_integrite` ;
- supprimer un invité qui a une invitation **efface l'invitation en cascade** (confirmé) ; le `DELETE`
  conditionnel (`NOT EXISTS`) du module ne supprime alors rien ;
- **12 insertions concurrentes** du même couple : 1 réussit, 11 sont rejetées par la contrainte d'unicité ;
- `docker compose config` valide `docker-compose.yml` et le service de `deploy/compose-invites-service.yaml`
  fusionné dans le `compose.yaml` de l'équipe.

**Ce qui n'a PAS été exécuté** : les routes FastAPI, les modèles et dépôts SQLAlchemy, les gestionnaires
d'erreurs FastAPI, l'**image Docker** (construction impossible sans réseau), le démarrage du conteneur et
l'ensemble des tests d'intégration. Lancez `pytest -v -rs` puis les tests d'intégration, construisez
l'image, et corrigez ce qui échouerait : des écarts propres à ces bibliothèques ne sont pas exclus.

## Intégration avec le projet principal

Le dépôt de l'équipe (`ysn133/event-management-system`) est une architecture distribuée : services
Spring Boot (événements, budget), ASP.NET Core (salles), frontend React, passerelle (vide pour
l'instant), le tout sur **une base PostgreSQL commune**. Ce module est un service de plus.

| Constat dans le dépôt de l'équipe | Conséquence pour le module |
|---|---|
| Le service Événements expose `GET /api/evenements/{id}` (port 5001, contrat dans `docs/api-contracts/evenement.openapi.yaml`) | `EvenementClient` appelle cette route ; le module **ne lit pas** la table `evenements`. |
| Les ports 5001 (événements), 5004 (budget) et 5242 (salles) sont pris | Ce module écoute sur **5003**. |
| Base commune : PostgreSQL 18, `localhost:5433`, base `eventflow`, tables créées par `database/init` | `DATABASE_URL` ; base de test sur le port 5434. Pas de migration. |
| Données partagées : organisateur `1` et événement `1` (`002_seed.sql`) | Utilisables pour essayer le module (`evenement_id = 1`). Ne pas les supprimer. |
| **Aucune authentification** dans le dépôt | Le point d'intégration reste **non connecté** (voir ci-dessous). |
| Le service Événements est sur `/api/evenements` | Ce module expose `/api/v1/…` : voir *Décisions à prendre par l'équipe*. |

### Ce que le module demande au service Événements

Une seule route : `GET {EVENEMENTS_SERVICE_URL}/api/evenements/{id}`, réponse attendue
`{id, organisateurId, titre, description, dateDebut, dateFin, lieu, statut}` (dates ISO 8601).

| Réponse du service | Réponse de ce module |
|---|---|
| 200 | l'événement est utilisé (titre, lieu et date pour l'invitation, `organisateurId` pour le contrôle d'accès) |
| 404 | 404 `EVENEMENT_INTROUVABLE` |
| 5xx, service arrêté, délai dépassé | 503 `SERVICE_EVENEMENTS_INDISPONIBLE` |
| autre 4xx, JSON invalide, champ manquant | 502 `REPONSE_EVENEMENTS_INVALIDE` |

Si le contrat du service change, seul `app/clients/evenement_client.py` est à adapter.

### Utiliser la clé étrangère

`invitations.evenement_id` référence `evenements(id)` en `ON DELETE RESTRICT` : un événement qui a des
invitations **ne peut pas être supprimé** tant qu'elles existent. C'est au module Événements de décider
quoi faire (refuser, ou supprimer d'abord les invitations). L'erreur de clé étrangère lors d'une
association vers un événement supprimé entre-temps est traduite en 404.

### Décisions à prendre par l'équipe

1. **Format des erreurs.** Les services Java renvoient `{"success": false, "code": "…", "message": "…",
   "fieldErrors": […]}` ; ce module renvoie `{"error": {"code", "message", "details"}}`. Un frontend
   partagé devra gérer les deux, ou l'un des deux formats devra changer (modification de
   `app/api/error_handlers.py` et `app/api/responses.py`, sans impact sur les règles métier).
2. **Préfixe des routes.** Le service Événements utilise `/api/evenements`, ce module `/api/v1/…`
   (cahier des charges). À aligner au niveau de la passerelle ou du préfixe.
3. **Ajout du `CHECK` sur `invitations.statut`** (`sql/ameliorations_recommandees.sql`) : à valider,
   le schéma est partagé.
4. **Statut de l'événement** : faut-il interdire d'inviter sur un événement annulé ou terminé ?
   Aucune règle n'étant définie, rien n'est bloqué.
5. **Dockerisation du service Événements** (et des autres) : pour que tout démarre avec une seule
   commande, chaque service aura besoin de son Dockerfile et de son entrée dans `compose.yaml`.

### Fichiers à adapter

| Fichier | Quand |
|---|---|
| `.env` / `app/core/config.py` | Toujours : `DATABASE_URL`, `EVENEMENTS_SERVICE_URL`, `CORS_ORIGINS`, `APP_ENV`. |
| `app/clients/evenement_client.py` | Si le contrat du service Événements change (noms de champs, route). |
| `app/core/security.py` | Pour brancher l'authentification (ci-dessous). |
| `app/api/error_handlers.py`, `app/api/responses.py` | Pour aligner le format d'erreur sur celui des autres services. |
| `app/services/notification_service.py` | Pour brancher un vrai fournisseur d'e-mail ou de SMS. |

### Brancher l'authentification du projet principal

Le dépôt n'en contient pas encore. Quand elle existera :

```python
from app.core.security import CurrentUser, set_identity_provider

class MonFournisseur:
    def authenticate(self, request) -> CurrentUser | None:
        ...  # lire le jeton/la session, retourner CurrentUser(id=<id organisateur>) ou None

set_identity_provider(MonFournisseur())
```

Puis `AUTH_ENABLED=true` et, si l'identifiant utilisateur est un `organisateurs.id` (c'est la valeur de
`organisateurId` renvoyée par le service Événements), `ORGANISER_CHECK_ENABLED=true`.

### Responsabilités qui restent au module Événements

Création, modification et suppression des événements ; schéma de `evenements` ; signification des
statuts d'événement ; sort des invitations d'un événement annulé ou supprimé (aujourd'hui la clé
étrangère bloque la suppression).

### Brancher un vrai fournisseur d'envoi

Créez une sous-classe de `NotificationSender` (méthode `envoyer`) qui retourne
`ResultatEnvoi(confirme=True, …)` **uniquement** si le fournisseur a accepté le message, puis
enregistrez-la dans `construire_sender`. `date_envoi` n'est écrite que sur confirmation.

## Récapitulatif des fonctionnalités

| Cahier des charges | Réalisation |
|---|---|
| A. Invités : CRUD, 3 recherches, validation | Fait (validation Pydantic avant insertion). |
| B. Associations : création, 2 listes, suppression, doublons interdits, concurrence | Fait (contrainte `UNIQUE` + traduction en 409). |
| C. Invitations : création/envoi, `date_envoi`, consultation, listes, statut, suivi | Fait. |
| D. Statistiques par événement | Fait (une requête agrégée). |
| E. Envoi : interface, simulation, `date_envoi` sur confirmation, réponse ≠ envoi, honnêteté | Fait. |
| Sécurité : validation, SQL paramétré, transactions, `.env`, erreurs centralisées, CORS, autorisation | Fait. L'authentification **n'est pas intégrée** (point d'intégration seulement). |

## Limites connues

1. **Envois simultanés** : deux requêtes d'envoi strictement simultanées pour la même invitation peuvent
   toutes deux atteindre le fournisseur ; une seule `date_envoi` est enregistrée et l'autre requête reçoit
   un 409. Une table d'envoi ou un mécanisme **Outbox** (idempotence côté fournisseur) le résoudrait ;
   `sql/ameliorations_optionnelles.sql` en prépare le terrain.
2. **Échecs d'envoi non conservés** en base (schéma inchangé) : seulement dans la réponse 502 et les journaux.
3. **Aucun fournisseur réel** : seuls `NotConfiguredSender` (défaut) et la simulation existent.
4. **Authentification non intégrée** ; contrôle de l'organisateur codé mais désactivé par défaut.
5. **E-mails internationalisés** (caractères non ASCII) refusés ; pas de vérification que l'adresse existe.
6. **Aucune restriction selon le statut de l'événement** (annulé, terminé…), faute de règles connues.
7. **Suppression d'invité** : le `DELETE` conditionnel évite la cascade de la base commune, mais une invitation
   créée exactement pendant la suppression reste un cas limite que seuls les tests d'intégration peuvent confirmer.
8. **Conventions d'API différentes** des autres services (format d'erreur, préfixe) : voir *Décisions à prendre par l'équipe*.
9. **Dépendance au service Événements** : s'il est arrêté, l'association, l'envoi et les statistiques répondent 503. La liste « événements d'un invité » fait un appel par événement distinct (au plus 100 par page).
10. **Code et image Docker non exécutés de bout en bout** dans l'environnement de rédaction (voir le rapport de tests).
