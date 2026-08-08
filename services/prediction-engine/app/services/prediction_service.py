import os
import joblib
import numpy as np
import pandas as pd
from datetime import datetime
from pathlib import Path
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from app.schemas.metrics import MetricInput, PredictionResult

MODEL_PATH = Path("app/models/isolation_forest.pkl")
SCALER_PATH = Path("app/models/scaler.pkl")

FEATURE_COLUMNS = [
    "cpu_usage", "memory_usage", "disk_usage",
    "network_in_mbps", "network_out_mbps",
    "response_time_ms", "error_rate"
]


class PredictionService:
    def __init__(self):
        self.model: IsolationForest | None = None
        self.scaler: StandardScaler | None = None
        self._load_model()

    def _load_model(self):
        """Charge le modèle depuis le disque s'il existe."""
        if MODEL_PATH.exists() and SCALER_PATH.exists():
            self.model = joblib.load(MODEL_PATH)
            self.scaler = joblib.load(SCALER_PATH)
            print("Isolation Forest model loaded.")
        else:
            print("No model found. Train it via /api/prediction/train")

    def is_ready(self) -> bool:
        return self.model is not None and self.scaler is not None

    def _extract_features(self, metric: MetricInput) -> np.ndarray:
        """Extrait le vecteur de features depuis les métriques."""
        return np.array([[
            metric.cpu_usage,
            metric.memory_usage,
            metric.disk_usage,
            metric.network_in_mbps,
            metric.network_out_mbps,
            metric.response_time_ms if metric.response_time_ms is not None else 0.0,
            metric.error_rate if metric.error_rate is not None else 0.0,
        ]])

    def _determine_severity(self, score: float, is_anomaly: bool) -> str:
        if not is_anomaly:
            return "NORMAL"
        if score < -0.3:
            return "CRITICAL"
        return "WARNING"

    def _identify_anomalous_metrics(self, metric: MetricInput) -> list[str]:
        """Identifie les métriques qui dépassent des seuils critiques."""
        anomalous = []
        if metric.cpu_usage > 85:
            anomalous.append(f"cpu_usage={metric.cpu_usage:.1f}%")
        if metric.memory_usage > 90:
            anomalous.append(f"memory_usage={metric.memory_usage:.1f}%")
        if metric.disk_usage > 90:
            anomalous.append(f"disk_usage={metric.disk_usage:.1f}%")
        if metric.response_time_ms and metric.response_time_ms > 2000:
            anomalous.append(f"response_time={metric.response_time_ms:.0f}ms")
        if metric.error_rate and metric.error_rate > 5:
            anomalous.append(f"error_rate={metric.error_rate:.1f}%")
        return anomalous

    def predict(self, metric: MetricInput) -> PredictionResult:
        """Analyse une métrique et retourne le résultat de prédiction."""
        if not self.is_ready():
            # Fallback sur des règles simples si pas de modèle
            return self._rule_based_prediction(metric)

        features = self._extract_features(metric)
        features_scaled = self.scaler.transform(features)

        # Isolation Forest: -1 = anomalie, 1 = normal
        prediction = self.model.predict(features_scaled)[0]
        score = self.model.score_samples(features_scaled)[0]

        is_anomaly = prediction == -1
        severity = self._determine_severity(score, is_anomaly)
        anomalous_metrics = self._identify_anomalous_metrics(metric)

        recommendation = None
        if is_anomaly:
            recommendation = self._generate_recommendation(metric, anomalous_metrics)

        return PredictionResult(
            resource_id=metric.resource_id,
            resource_name=metric.resource_name,
            timestamp=metric.timestamp or datetime.utcnow(),
            is_anomaly=is_anomaly,
            anomaly_score=float(score),
            confidence=min(abs(score), 1.0),
            severity=severity,
            anomalous_metrics=anomalous_metrics,
            recommendation=recommendation
        )

    def _rule_based_prediction(self, metric: MetricInput) -> PredictionResult:
        """Prédiction basée sur des règles simples (fallback sans modèle)."""
        anomalous_metrics = self._identify_anomalous_metrics(metric)
        is_anomaly = len(anomalous_metrics) > 0

        score = -0.5 if is_anomaly else 0.5
        severity = "CRITICAL" if len(anomalous_metrics) >= 2 else ("WARNING" if is_anomaly else "NORMAL")

        recommendation = self._generate_recommendation(metric, anomalous_metrics) if is_anomaly else None

        return PredictionResult(
            resource_id=metric.resource_id,
            resource_name=metric.resource_name,
            timestamp=metric.timestamp or datetime.utcnow(),
            is_anomaly=is_anomaly,
            anomaly_score=score,
            confidence=0.7,
            severity=severity,
            anomalous_metrics=anomalous_metrics,
            recommendation=recommendation
        )

    def _generate_recommendation(self, metric: MetricInput, anomalous_metrics: list[str]) -> str:
        """Génère une recommandation basée sur les métriques anormales."""
        if metric.cpu_usage > 85:
            return "CPU élevé — vérifier les processus consommateurs, envisager un redémarrage ou scaling."
        if metric.memory_usage > 90:
            return "Mémoire saturée — libérer le cache ou redémarrer le service."
        if metric.disk_usage > 90:
            return "Disque presque plein — nettoyer les logs ou archiver les données."
        if metric.response_time_ms and metric.response_time_ms > 2000:
            return "Temps de réponse élevé — vérifier la base de données et les dépendances réseau."
        if metric.error_rate and metric.error_rate > 5:
            return "Taux d'erreur élevé — analyser les logs d'application pour identifier la cause."
        return "Anomalie détectée — investigation manuelle recommandée."

    def train(self, data: pd.DataFrame, contamination: float = 0.05) -> dict:
        """Entraîne le modèle Isolation Forest sur un dataset.

        contamination doit refléter la proportion réelle d'anomalies attendue
        dans les données d'entraînement — un contamination trop bas par rapport
        au taux réel dilue la capacité du modèle à isoler les vraies anomalies.
        """
        features = data[FEATURE_COLUMNS].fillna(0)

        self.scaler = StandardScaler()
        features_scaled = self.scaler.fit_transform(features)

        self.model = IsolationForest(
            n_estimators=100,
            contamination=contamination,
            random_state=42,
            n_jobs=-1
        )
        self.model.fit(features_scaled)

        # Sauvegarde
        MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(self.model, MODEL_PATH)
        joblib.dump(self.scaler, SCALER_PATH)

        return {
            "status": "trained",
            "samples": len(features),
            "features": FEATURE_COLUMNS,
            "contamination": contamination
        }


# Instance singleton
prediction_service = PredictionService()
