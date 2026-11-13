# Microservice prestataires

## Lancement local

Prérequis : Node.js 18+ et PostgreSQL. À la racine du dépôt, copiez `.env.example`
vers `.env`, puis renseignez `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` et
`DB_PASSWORD`. `DATABASE_URL` est facultative et prioritaire lorsqu’elle est
renseignée. `DB_SSL=true` active TLS avec vérification du certificat ; laissez-le
à `false` pour une base PostgreSQL locale.

Installez et démarrez le service depuis son répertoire :

```sh
cd services/prestataires
npm ci
npm run db:setup
npm start
```

Le service écoute sur le port `5002` par défaut (modifiable via `PORT`). Le fichier
`.env` est chargé depuis la racine du dépôt, quel que soit le répertoire courant.
L’API détaillée est décrite dans [`prestataires-openapi.yaml`](prestataires-openapi.yaml).

## Tests automatisés

Depuis `services/prestataires`, exécutez `npm test`. Les tests Node.js utilisent
un pool PostgreSQL simulé et ne modifient aucune base. Le rapport détaillé est
disponible dans [`../tests/rapport-tests-unitaires-prestataires.pdf`](../tests/rapport-tests-unitaires-prestataires.pdf).

## Contrat et intégration

- Préfixe : `/api/prestataires`; les identifiants doivent être des entiers positifs.
- Un prestataire contient `id`, `nom`, `contact`, `typeService` et `tarif`.
  Le détail par identifiant inclut aussi `services`.
- Un service contient `id`, `prestataireId`, `nom`, `description` (nullable) et
  `tarif`. Les tarifs sont des nombres positifs ou nuls.
- Les erreurs sont au format `{ "success": false, "message": "..." }`. Les routes
  renvoient notamment `400` pour une requête invalide, `404` pour une ressource
  absente, `409` pour un prestataire référencé, et `204` sans corps après une
  suppression réussie.
- La suppression d’un prestataire interroge `contrats_prestataire`. Cette table
  appartient au service contrats/intégration et n’est volontairement pas créée par
  `database/prestataires.sql`. Pour utiliser la suppression dans l’environnement
  intégré, cette table doit exister avec une colonne `prestataire_id`; une clé
  étrangère vers `prestataires(id)` permet aussi de protéger les suppressions
  concurrentes. Sans cette dépendance, cette route renverra une erreur serveur.
