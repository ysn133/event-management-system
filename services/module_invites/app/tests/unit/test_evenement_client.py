"""Client du service Événements, testé contre un VRAI petit serveur HTTP local (aucun mock réseau)."""

import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from app.clients.evenement_client import EvenementClient, lire_evenement
from app.core.exceptions import ServiceUnavailableError, UpstreamError

# Forme exacte de EvenementResponse (service Java) : dates ISO 8601 avec décalage.
EVENEMENT_JSON = {
    "id": 1,
    "organisateurId": 7,
    "titre": "Conférence annuelle",
    "description": None,
    "dateDebut": "2026-12-01T09:00:00Z",
    "dateFin": "2026-12-01T11:00:00+00:00",
    "lieu": "Salle A",
    "statut": "PLANIFIE",
}


class _Service(BaseHTTPRequestHandler):
    reponses: dict[str, tuple[int, bytes]] = {}
    chemins_appeles: list[str] = []

    def do_GET(self):  # noqa: N802
        type(self).chemins_appeles.append(self.path)
        statut, corps = type(self).reponses.get(self.path, (404, b'{"success":false,"code":"RESOURCE_NOT_FOUND"}'))
        self.send_response(statut)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(corps)

    def log_message(self, *args):
        pass


@pytest.fixture
def service():
    _Service.reponses = {}
    _Service.chemins_appeles = []
    serveur = HTTPServer(("127.0.0.1", 0), _Service)
    fil = threading.Thread(target=serveur.serve_forever, daemon=True)
    fil.start()
    yield f"http://127.0.0.1:{serveur.server_port}", _Service
    serveur.shutdown()
    serveur.server_close()


def test_evenement_existant(service):
    url, faux = service
    faux.reponses["/api/evenements/1"] = (200, json.dumps(EVENEMENT_JSON).encode())
    evenement = EvenementClient(url).get(1)
    assert (evenement.id, evenement.organisateur_id, evenement.titre, evenement.lieu) == (
        1, 7, "Conférence annuelle", "Salle A",
    )
    assert evenement.date_debut.year == 2026 and evenement.date_debut.tzinfo is not None
    assert faux.chemins_appeles == ["/api/evenements/1"]


def test_evenement_inexistant_retourne_none(service):
    url, _ = service
    assert EvenementClient(url).get(999) is None


def test_url_de_base_avec_slash_final(service):
    url, faux = service
    faux.reponses["/api/evenements/1"] = (200, json.dumps(EVENEMENT_JSON).encode())
    assert EvenementClient(url + "/").get(1) is not None


def test_service_en_erreur_500_devient_503(service):
    url, faux = service
    faux.reponses["/api/evenements/1"] = (500, b"{}")
    with pytest.raises(ServiceUnavailableError) as exc:
        EvenementClient(url).get(1)
    assert exc.value.status_code == 503 and exc.value.code == "SERVICE_EVENEMENTS_INDISPONIBLE"


def test_service_arrete_devient_503():
    # Port 1 : rien n'écoute.
    with pytest.raises(ServiceUnavailableError):
        EvenementClient("http://127.0.0.1:1", timeout=1).get(1)


def test_reponse_400_devient_502(service):
    url, faux = service
    faux.reponses["/api/evenements/1"] = (400, b"{}")
    with pytest.raises(UpstreamError) as exc:
        EvenementClient(url).get(1)
    assert exc.value.status_code == 502


def test_json_invalide_devient_502(service):
    url, faux = service
    faux.reponses["/api/evenements/1"] = (200, b"<html>pas du json</html>")
    with pytest.raises(UpstreamError):
        EvenementClient(url).get(1)


@pytest.mark.parametrize("champ", ["id", "organisateurId", "titre", "dateDebut", "dateFin", "statut"])
def test_champ_obligatoire_manquant_devient_502(champ):
    corps = {k: v for k, v in EVENEMENT_JSON.items() if k != champ}
    with pytest.raises(UpstreamError):
        lire_evenement(corps)


def test_date_invalide_devient_502():
    with pytest.raises(UpstreamError):
        lire_evenement({**EVENEMENT_JSON, "dateDebut": "pas une date"})


def test_corps_qui_nest_pas_un_objet_devient_502():
    with pytest.raises(UpstreamError):
        lire_evenement([1, 2, 3])
