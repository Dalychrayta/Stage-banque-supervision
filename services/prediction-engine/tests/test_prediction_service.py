import pytest

from app.schemas.metrics import MetricInput
from app.services.prediction_service import PredictionService


def make_metric(**overrides) -> MetricInput:
    base = dict(
        resource_id="srv-001",
        resource_name="test-server",
        resource_type="SERVER",
        cpu_usage=30.0,
        memory_usage=40.0,
        disk_usage=50.0,
        network_in_mbps=100.0,
        network_out_mbps=50.0,
        response_time_ms=150.0,
        error_rate=0.5,
    )
    base.update(overrides)
    return MetricInput(**base)


@pytest.fixture
def service_without_model() -> PredictionService:
    """Instance du service forcée en mode 'règles de secours' (pas de modèle chargé)."""
    svc = PredictionService()
    svc.model = None
    svc.scaler = None
    return svc


class TestIdentifyAnomalousMetrics:

    def test_normal_metrics_have_no_anomalies(self, service_without_model):
        metric = make_metric()
        assert service_without_model._identify_anomalous_metrics(metric) == []

    def test_high_cpu_is_flagged(self, service_without_model):
        metric = make_metric(cpu_usage=90.0)
        anomalies = service_without_model._identify_anomalous_metrics(metric)
        assert any("cpu_usage" in a for a in anomalies)

    def test_high_memory_is_flagged(self, service_without_model):
        metric = make_metric(memory_usage=95.0)
        anomalies = service_without_model._identify_anomalous_metrics(metric)
        assert any("memory_usage" in a for a in anomalies)

    def test_high_disk_is_flagged(self, service_without_model):
        metric = make_metric(disk_usage=93.0)
        anomalies = service_without_model._identify_anomalous_metrics(metric)
        assert any("disk_usage" in a for a in anomalies)

    def test_high_response_time_is_flagged(self, service_without_model):
        metric = make_metric(response_time_ms=2500.0)
        anomalies = service_without_model._identify_anomalous_metrics(metric)
        assert any("response_time" in a for a in anomalies)

    def test_high_error_rate_is_flagged(self, service_without_model):
        metric = make_metric(error_rate=12.0)
        anomalies = service_without_model._identify_anomalous_metrics(metric)
        assert any("error_rate" in a for a in anomalies)


class TestRuleBasedPrediction:

    def test_normal_metric_is_not_anomaly(self, service_without_model):
        result = service_without_model.predict(make_metric())
        assert result.is_anomaly is False
        assert result.severity == "NORMAL"
        assert result.recommendation is None

    def test_single_anomalous_metric_is_warning(self, service_without_model):
        result = service_without_model.predict(make_metric(cpu_usage=90.0))
        assert result.is_anomaly is True
        assert result.severity == "WARNING"
        assert result.recommendation is not None

    def test_multiple_anomalous_metrics_is_critical(self, service_without_model):
        result = service_without_model.predict(make_metric(cpu_usage=95.0, memory_usage=95.0))
        assert result.is_anomaly is True
        assert result.severity == "CRITICAL"

    def test_result_preserves_resource_identity(self, service_without_model):
        metric = make_metric(resource_id="srv-042", resource_name="payment-server")
        result = service_without_model.predict(metric)
        assert result.resource_id == "srv-042"
        assert result.resource_name == "payment-server"


class TestModelReadiness:

    def test_is_ready_false_without_model(self, service_without_model):
        assert service_without_model.is_ready() is False

    def test_train_makes_service_ready(self, tmp_path, monkeypatch):
        import pandas as pd
        import numpy as np
        import app.services.prediction_service as ps_module

        # Redirige la sauvegarde du modèle vers un dossier temporaire pour ne pas
        # écraser le modèle réel utilisé par le service en cours d'exécution.
        monkeypatch.setattr(ps_module, "MODEL_PATH", tmp_path / "isolation_forest.pkl")
        monkeypatch.setattr(ps_module, "SCALER_PATH", tmp_path / "scaler.pkl")

        svc = PredictionService()
        rng = np.random.default_rng(42)
        df = pd.DataFrame({
            "cpu_usage": rng.uniform(20, 60, 200),
            "memory_usage": rng.uniform(20, 60, 200),
            "disk_usage": rng.uniform(20, 60, 200),
            "network_in_mbps": rng.uniform(50, 150, 200),
            "network_out_mbps": rng.uniform(20, 80, 200),
            "response_time_ms": rng.uniform(50, 300, 200),
            "error_rate": rng.uniform(0, 2, 200),
        })

        result = svc.train(df, contamination=0.05)

        assert result["status"] == "trained"
        assert result["samples"] == 200
        assert svc.is_ready() is True
        assert (tmp_path / "isolation_forest.pkl").exists()
