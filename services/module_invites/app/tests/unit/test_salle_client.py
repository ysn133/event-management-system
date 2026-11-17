"""Client du service Salles, contre un vrai petit serveur HTTP local."""

import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from app.clients.salle_client import SalleClient
from app.core.exceptions import ServiceUnavailableError, UpstreamError


class _Service(BaseHTTPRequestHandler):
    reponses: dict[str, tuple[int, object]] = {}
    entetes: list[str | None] = []

    def do_GET(self):  # noqa: N802
        type(self).entetes.append(self.headers.get("Authorization"))
        statut, corps = type(self).reponses.get(self.path, (404, {}))
        self.send_response(statut)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(corps).encode())

    def log_message(self, *args):
        pass


@pytest.fixture
def service():
    _Service.reponses, _Service.entetes = {}, []
    serveur = HTTPServer(("127.0.0.1", 0), _Service)
    threading.Thread(target=serveur.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{serveur.server_port}", _Service
    serveur.shutdown()
    serveur.server_close()


def _reservation(salle_id, statut="CONFIRMEE"):
    return {"salleId": salle_id, "statut": statut, "nomSalle": f"S{salle_id}"}


def test_capacite_dune_salle_confirmee(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (200, [_reservation(4)])
    faux.reponses["/api/salles/4"] = (200, {"id": 4, "nom": "Grande salle", "capacite": 120})
    assert SalleClient(url).capacite_evenement(1).total == 120


def test_reservation_annulee_ignoree_et_salle_comptee_une_fois(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (
        200, [_reservation(4), _reservation(4, "EN_ATTENTE"), _reservation(5, "ANNULEE"), _reservation(6)],
    )
    faux.reponses["/api/salles/4"] = (200, {"capacite": 50})
    faux.reponses["/api/salles/6"] = (200, {"capacite": 30})
    assert SalleClient(url).capacite_evenement(1).total == 80


def test_aucune_reservation_donne_aucune_limite(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (200, [])
    assert SalleClient(url).capacite_evenement(1).total is None
    assert SalleClient(url).capacite_evenement(99).total is None  # 404


def test_salle_absente_est_ignoree(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (200, [_reservation(4)])
    assert SalleClient(url).capacite_evenement(1).total is None


def test_reponses_invalides(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (200, {"pas": "une liste"})
    with pytest.raises(UpstreamError):
        SalleClient(url).capacite_evenement(1)
    faux.reponses["/api/reservations/evenement/2"] = (200, [_reservation(4)])
    faux.reponses["/api/salles/4"] = (200, {"capacite": 0})
    with pytest.raises(UpstreamError):
        SalleClient(url).capacite_evenement(2)


def test_erreur_serveur_donne_503(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (500, {})
    with pytest.raises(ServiceUnavailableError):
        SalleClient(url).capacite_evenement(1)


def test_service_injoignable_donne_503():
    with pytest.raises(ServiceUnavailableError):
        SalleClient("http://127.0.0.1:1", timeout=1).capacite_evenement(1)


def test_jeton_retransmis(service):
    url, faux = service
    faux.reponses["/api/reservations/evenement/1"] = (200, [])
    SalleClient(url).with_token("abc").capacite_evenement(1)
    assert faux.entetes == ["Bearer abc"]
