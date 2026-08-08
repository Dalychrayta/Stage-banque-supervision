from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class MetricInput(BaseModel):
    """Métriques d'une ressource supervisée envoyées pour analyse."""
    resource_id: str = Field(..., description="Identifiant unique de la ressource")
    resource_name: str = Field(..., description="Nom de la ressource")
    resource_type: str = Field(..., description="Type: SERVER, VM, APP, DB")
    timestamp: Optional[datetime] = Field(default_factory=datetime.utcnow)

    # Métriques système
    cpu_usage: float = Field(..., ge=0, le=100, description="Utilisation CPU en %")
    memory_usage: float = Field(..., ge=0, le=100, description="Utilisation mémoire en %")
    disk_usage: float = Field(..., ge=0, le=100, description="Utilisation disque en %")
    network_in_mbps: float = Field(..., ge=0, description="Trafic réseau entrant Mbps")
    network_out_mbps: float = Field(..., ge=0, description="Trafic réseau sortant Mbps")

    # Métriques applicatives (optionnelles)
    response_time_ms: Optional[float] = Field(None, ge=0, description="Temps de réponse en ms")
    error_rate: Optional[float] = Field(None, ge=0, le=100, description="Taux d'erreur en %")
    request_count: Optional[int] = Field(None, ge=0, description="Nombre de requêtes")

    class Config:
        json_schema_extra = {
            "example": {
                "resource_id": "srv-001",
                "resource_name": "payment-server-01",
                "resource_type": "SERVER",
                "cpu_usage": 87.5,
                "memory_usage": 92.1,
                "disk_usage": 65.0,
                "network_in_mbps": 120.5,
                "network_out_mbps": 85.2,
                "response_time_ms": 1500.0,
                "error_rate": 5.2
            }
        }


class PredictionResult(BaseModel):
    """Résultat de l'analyse de prédiction."""
    resource_id: str
    resource_name: str
    timestamp: datetime
    is_anomaly: bool
    anomaly_score: float = Field(..., description="Score d'anomalie (plus élevé = plus anormal)")
    confidence: float = Field(..., ge=0, le=1, description="Niveau de confiance")
    severity: str = Field(..., description="NORMAL, WARNING, CRITICAL")
    anomalous_metrics: list[str] = Field(default_factory=list, description="Métriques anormales détectées")
    recommendation: Optional[str] = None
