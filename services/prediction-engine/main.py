from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.routers import prediction, health

app = FastAPI(
    title="BCT Prediction Engine",
    description="Détection d'anomalies et prédiction de pannes — Banque Centrale de Tunisie",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, prefix="/health", tags=["Health"])
app.include_router(prediction.router, prefix="/api/prediction", tags=["Prediction"])


@app.get("/")
def root():
    return {"service": "prediction-engine", "status": "running", "version": "1.0.0"}
