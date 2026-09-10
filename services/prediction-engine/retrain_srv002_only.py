"""
Entraîne le modèle Isolation Forest UNIQUEMENT sur les vraies données de
srv-002 (la seule ressource réelle surveillée), en excluant la journée du
13 août (test de pression mémoire volontaire — 42% de relevés anormaux ce
jour-là, contre ~12% les autres jours).

Validation honnête : on ne teste pas sur 2 lignes choisies à la main, mais
on mesure l'accord entre le modèle et nos règles à seuils sur TOUTES les
lignes réelles (celles que les règles jugent normales doivent être jugées
normales par le modèle, et inversement).
"""
import shutil
from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

DATA_PATH = Path("data/srv002_full.csv")
MODEL_PATH = Path("app/models/isolation_forest.pkl")
SCALER_PATH = Path("app/models/scaler.pkl")

COLS = ["cpu_usage", "memory_usage", "disk_usage",
        "network_in_mbps", "network_out_mbps",
        "response_time_ms", "error_rate"]

EXCLUDE_DAY = "2026-08-13"  # test de pression mémoire volontaire


def rule_anomaly(df: pd.DataFrame) -> pd.Series:
    return (
        (df["cpu_usage"] > 85)
        | (df["memory_usage"] > 90)
        | (df["disk_usage"] > 90)
        | (df["response_time_ms"] > 2000)
        | (df["error_rate"] > 5)
    )


def main():
    df = pd.read_csv(DATA_PATH, header=None, names=COLS + ["day"])
    for c in COLS:
        df[c] = pd.to_numeric(df[c], errors="coerce")
    df = df.dropna(subset=COLS).reset_index(drop=True)

    before = len(df)
    df = df[df["day"] != EXCLUDE_DAY].reset_index(drop=True)
    print(f"Relevés srv-002 : {before} -> {len(df)} après exclusion du {EXCLUDE_DAY}")

    is_anom = rule_anomaly(df)
    contamination = float(min(max(is_anom.mean(), 0.001), 0.5))
    print(f"Contamination (nos seuils) : {contamination:.4f} "
          f"({int(is_anom.sum())} anormaux / {len(df)})")

    X = df[COLS].fillna(0)
    scaler = StandardScaler()
    Xs = scaler.fit_transform(X)

    model = IsolationForest(n_estimators=100, contamination=contamination,
                            random_state=42, n_jobs=-1)
    model.fit(Xs)

    pred = model.predict(Xs)  # -1 = anomalie, 1 = normal
    normal_mask = ~is_anom
    agree_normal = ((pred == 1) & normal_mask).sum() / normal_mask.sum()
    agree_anom = ((pred == -1) & is_anom).sum() / max(is_anom.sum(), 1)
    print(f"\nAccord modèle vs règles :")
    print(f"  lignes jugées NORMALES par les règles, confirmées normales par le modèle : {agree_normal:.1%}")
    print(f"  lignes jugées ANORMALES par les règles, confirmées anormales par le modèle : {agree_anom:.1%}")

    if MODEL_PATH.exists():
        shutil.copy(MODEL_PATH, MODEL_PATH.with_suffix(".prev.pkl.bak"))
        shutil.copy(SCALER_PATH, SCALER_PATH.with_suffix(".prev.pkl.bak"))
    joblib.dump(model, MODEL_PATH)
    joblib.dump(scaler, SCALER_PATH)
    print(f"\nModèle sauvegardé : {MODEL_PATH}")

    # Contrôle : une ligne au centre de la plage normale réelle de srv-002
    med = df[normal_mask][COLS].median()
    s = scaler.transform(pd.DataFrame([med])[COLS])
    verdict = "ANOMALIE" if model.predict(s)[0] == -1 else "NORMAL"
    print(f"\nRelevé 'normal typique' (médianes réelles srv-002) -> {verdict} "
          f"(score {model.score_samples(s)[0]:.3f})")
    print(med.round(1).to_string())


if __name__ == "__main__":
    main()
