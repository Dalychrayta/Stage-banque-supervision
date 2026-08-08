from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def sample_payload(**overrides):
    payload = {
        "resource_id": "srv-001",
        "resource_name": "payment-server-01",
        "resource_type": "SERVER",
        "cpu_usage": 87.5,
        "memory_usage": 92.1,
        "disk_usage": 65.0,
        "network_in_mbps": 120.5,
        "network_out_mbps": 85.2,
        "response_time_ms": 1500.0,
        "error_rate": 5.2,
    }
    payload.update(overrides)
    return payload


def test_root_endpoint_reports_service_identity():
    response = client.get("/")
    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "prediction-engine"
    assert body["status"] == "running"


def test_health_endpoint_reports_model_status():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert isinstance(body["model_loaded"], bool)


def test_model_status_endpoint():
    response = client.get("/api/prediction/model/status")
    assert response.status_code == 200
    body = response.json()
    assert "model_loaded" in body
    assert "model_type" in body


def test_analyze_returns_well_formed_prediction():
    response = client.post("/api/prediction/analyze", json=sample_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["resource_id"] == "srv-001"
    assert isinstance(body["is_anomaly"], bool)
    assert body["severity"] in ("NORMAL", "WARNING", "CRITICAL")
    assert isinstance(body["anomalous_metrics"], list)


def test_analyze_rejects_missing_required_field():
    payload = sample_payload()
    del payload["cpu_usage"]
    response = client.post("/api/prediction/analyze", json=payload)
    assert response.status_code == 422


def test_analyze_rejects_out_of_range_percentage():
    response = client.post("/api/prediction/analyze", json=sample_payload(cpu_usage=150.0))
    assert response.status_code == 422


def test_analyze_batch_returns_one_result_per_input():
    payloads = [sample_payload(resource_id="srv-a"), sample_payload(resource_id="srv-b")]
    response = client.post("/api/prediction/analyze/batch", json=payloads)
    assert response.status_code == 200
    body = response.json()
    assert len(body) == 2
    assert {r["resource_id"] for r in body} == {"srv-a", "srv-b"}


def test_train_rejects_non_csv_file():
    response = client.post(
        "/api/prediction/train",
        files={"file": ("data.txt", b"not,a,csv", "text/plain")},
    )
    assert response.status_code == 400
