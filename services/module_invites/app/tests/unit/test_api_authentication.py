"""Anonymous or invalid sessions must fail before reaching any repository."""
from fastapi.testclient import TestClient
from app.core.config import Settings
from app.main import create_app


def test_toutes_les_routes_metier_exigent_un_jwt():
    settings = Settings(app_env="test", debug=False,
        database_url="postgresql+psycopg://test:test@localhost:1/never_connect_test",
        cors_origins=("http://localhost:5173",), auth_enabled=True,
        organiser_check_enabled=False, notification_backend="none", jwt_secret="x" * 40)
    with TestClient(create_app(settings=settings)) as client:
        assert client.get("/health").status_code == 200
        for method, path in [
            ("GET", "/api/v1/invites"), ("POST", "/api/v1/invites"),
            ("PUT", "/api/v1/invites/1"), ("DELETE", "/api/v1/invites/1"),
            ("POST", "/api/v1/evenements/1/invites/1"),
            ("DELETE", "/api/v1/evenements/1/invites/1"),
            ("POST", "/api/v1/evenements/1/invitations"),
            ("PATCH", "/api/v1/invitations/1/statut"),
            ("GET", "/api/v1/evenements/1/occupation"),
            ("GET", "/api/v1/evenements/1/statistiques-invitations"),
        ]:
            assert client.request(method, path, json={}).status_code == 401, (method, path)
            assert client.request(method, path, json={}, headers={"Authorization": "Bearer invalid"}).status_code == 401
        response = client.options("/api/v1/invites", headers={"Origin":"http://localhost:5173",
            "Access-Control-Request-Method":"GET", "Access-Control-Request-Headers":"Authorization"})
        assert response.status_code == 200
