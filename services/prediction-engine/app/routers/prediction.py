import io
import pandas as pd
from fastapi import APIRouter, HTTPException, UploadFile, File
from typing import List

from app.schemas.metrics import MetricInput, PredictionResult
from app.services.prediction_service import prediction_service

router = APIRouter()


@router.post("/analyze", response_model=PredictionResult)
def analyze_metric(metric: MetricInput):
    """Analyse une métrique et retourne si elle est anormale."""
    try:
        result = prediction_service.predict(metric)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/analyze/batch", response_model=List[PredictionResult])
def analyze_metrics_batch(metrics: List[MetricInput]):
    """Analyse un lot de métriques."""
    try:
        results = [prediction_service.predict(m) for m in metrics]
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/train")
async def train_model(file: UploadFile = File(...), contamination: float = 0.01):
    """Entraîne le modèle sur un fichier CSV de métriques historiques.

    contamination : proportion d'anomalies attendue dans les données (0.0-0.5).
    Attention, ce n'est pas une mesure mais un ORDRE : Isolation Forest
    découpera cette fraction des données comme atypique, même si elles sont
    toutes saines. À 0.05, sur des données normales collectées toutes les 30 s,
    cela produisait environ 160 fausses alertes par jour. La valeur par défaut
    est donc basse, et doit être relevée seulement si les données
    d'entraînement contiennent réellement une proportion d'anomalies connue.
    """
    if not file.filename.endswith(".csv"):
        raise HTTPException(status_code=400, detail="Fichier CSV requis.")
    try:
        contents = await file.read()
        df = pd.read_csv(io.StringIO(contents.decode("utf-8")))
        result = prediction_service.train(df, contamination=contamination)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/model/status")
def model_status():
    """Retourne l'état du modèle chargé."""
    return {
        "model_loaded": prediction_service.is_ready(),
        "model_type": "IsolationForest" if prediction_service.is_ready() else None
    }
