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

    def _severity_for(self, breaches: list[str], anomalous_metrics: list[str],
                      flagged_by_model: bool) -> str:
        """Gravite d'une anomalie explicable.

        - seuil absolu franchi          -> CRITICAL, la machine est deja en danger
        - deviation + profil atypique   -> CRITICAL, le modele confirme
        - deviation seule               -> WARNING, derive a surveiller
        """
        if not anomalous_metrics:
            return "NORMAL"
        if breaches:
            return "CRITICAL"
        return "CRITICAL" if flagged_by_model else "WARNING"

    # Au-dela de ce nombre d'ecarts-types par rapport a la normale apprise,
    # une metrique est consideree comme deviante. 3 est la convention usuelle :
    # environ 99.7% des mesures normales restent en dessous.
    DEVIATION_SIGMAS = 3.0

    def _absolute_threshold_breaches(self, metric: MetricInput) -> list[str]:
        """Seuils absolus de danger, independants de ce que le modele a appris.

        Ils restent necessaires : une machine a 95% de CPU est en danger meme si
        elle a toujours tourne ainsi, donc meme si le modele a appris que c'etait
        sa normale.
        """
        anomalous = []
        if metric.cpu_usage > 85:
            anomalous.append(f"cpu_usage={metric.cpu_usage:.1f}%")
        if metric.memory_usage > 90:
            anomalous.append(f"memory_usage={metric.memory_usage:.1f}%")
        if metric.disk_usage > 90:
            anomalous.append(f"disk_usage={metric.disk_usage:.1f}%")
        if metric.response_time_ms and metric.response_time_ms > 2000:
            anomalous.append(f"response_time_ms={metric.response_time_ms:.0f}ms")
        if metric.error_rate and metric.error_rate > 5:
            anomalous.append(f"error_rate={metric.error_rate:.1f}%")
        return anomalous

    def _statistical_deviations(self, metric: MetricInput) -> list[str]:
        """Metriques qui s'ecartent de la normale APPRISE par le modele.

        Sans ceci, le moteur ne savait nommer une metrique que si elle depassait
        un seuil catastrophique (>90%). Il pouvait donc declarer une anomalie
        tout en renvoyant une liste vide : le RCA ne recevait aucune piste et
        classait l'incident en UNKNOWN, ce qui laissait l'incident ouvert.

        Le scaler porte deja la moyenne et l'ecart-type appris par metrique :
        on s'en sert pour dire de combien chaque metrique devie, plutot que
        d'inventer de nouveaux seuils.
        """
        if self.scaler is None:
            return []
        values = self._extract_features(metric)[0]
        means = getattr(self.scaler, "mean_", None)
        scales = getattr(self.scaler, "scale_", None)
        if means is None or scales is None:
            return []

        deviations = []
        for name, value, mean, scale in zip(FEATURE_COLUMNS, values, means, scales):
            # Une metrique constante dans les donnees d'entrainement a un
            # ecart-type nul : toute comparaison y serait infinie, on l'ignore.
            if scale is None or scale <= 1e-9:
                continue
            z = (value - mean) / scale
            if abs(z) >= self.DEVIATION_SIGMAS:
                sens = "au-dessus" if z > 0 else "en-dessous"
                deviations.append((abs(z), f"{name}={value:.1f} ({abs(z):.1f} ecarts-types {sens} de la normale)"))
        # La metrique la plus deviante en premier : c'est la piste principale
        # pour le diagnostic.
        deviations.sort(key=lambda d: d[0], reverse=True)
        return [label for _, label in deviations]

    def _identify_anomalous_metrics(self, metric: MetricInput) -> list[str]:
        """Metriques a incriminer : depassement absolu d'abord, puis deviation apprise."""
        anomalous = self._absolute_threshold_breaches(metric)
        already_named = {label.split("=")[0] for label in anomalous}
        for deviation in self._statistical_deviations(metric):
            if deviation.split("=")[0] not in already_named:
                anomalous.append(deviation)
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

        flagged_by_model = prediction == -1
        breaches = self._absolute_threshold_breaches(metric)
        anomalous_metrics = self._identify_anomalous_metrics(metric)

        # On n'escalade que ce qu'on sait expliquer.
        #
        # Isolation Forest ne mesure pas une proportion d'anomalies, il en
        # decoupe une : le parametre contamination lui ORDONNE de considerer
        # cette fraction des donnees comme atypique, meme si tout est sain.
        # Observe en production : environ un incident toutes les 10 minutes,
        # dont les deux tiers sans aucune metrique deviante, donc classes
        # UNKNOWN par le RCA, donc jamais refermes. Une alerte que personne ne
        # peut expliquer n'aide personne et finit par etre ignoree — c'est la
        # fatigue d'alerte, et elle rend la supervision inutile.
        #
        # Un incident est donc ouvert des qu'une metrique est incriminable :
        # soit un seuil absolu de danger est franchi, soit la metrique s'ecarte
        # nettement de la normale APPRISE. Exiger en plus l'accord du modele
        # serait une erreur : il etoufferait des derives pourtant flagrantes
        # (une memoire a 12 ecarts-types au-dessus de son habitude) simplement
        # parce que le reste du profil reste ordinaire.
        #
        # Le modele garde un role reel : il dit si la mesure est atypique DANS
        # SON ENSEMBLE. Une metrique qui devie pendant que tout le reste est
        # normal est une derive a surveiller (WARNING) ; la meme metrique qui
        # devie alors que le profil entier est atypique est un vrai incident
        # (CRITICAL). Et un signal du modele que rien n'explique n'est pas
        # perdu : il ressort dans unexplained_model_flag.
        unexplained = flagged_by_model and not anomalous_metrics
        is_anomaly = bool(anomalous_metrics)

        severity = self._severity_for(breaches, anomalous_metrics, flagged_by_model)
        recommendation = self._generate_recommendation(metric, anomalous_metrics) if is_anomaly else None

        return PredictionResult(
            resource_id=metric.resource_id,
            resource_name=metric.resource_name,
            timestamp=metric.timestamp or datetime.utcnow(),
            is_anomaly=is_anomaly,
            anomaly_score=float(score),
            confidence=min(abs(score), 1.0),
            severity=severity,
            anomalous_metrics=anomalous_metrics,
            recommendation=recommendation,
            unexplained_model_flag=unexplained
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
