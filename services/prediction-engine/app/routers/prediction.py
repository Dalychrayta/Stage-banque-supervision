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
async def train_model(file: UploadFile = File(...)):
    """Entraîne le modèle sur un fichier CSV de métriques historiques."""
    if not file.filename.endswith(".csv"):
        raise HTTPException(status_code=400, detail="Fichier CSV requis.")
    try:
        contents = await file.read()
        df = pd.read_csv(io.StringIO(contents.decode("utf-8")))
        result = prediction_service.train(df)
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
