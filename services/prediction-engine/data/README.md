# Données du Prediction Engine

Le dossier `data/` est ignoré par Git (fichiers volumineux et/ou spécifiques
à un environnement).

## Modèle en production — entraîné sur nos propres données réelles

Le modèle utilisé par le service (`app/models/isolation_forest.pkl` +
`scaler.pkl`) est entraîné **uniquement sur les vraies métriques de
`srv-002`** (la seule ressource réelle surveillée), via le script
`retrain_srv002_only.py` à la racine du service.

Raison : la détection d'anomalie apprend le "normal" d'un système précis.
Le "normal" de `srv-002` ne peut venir que de son propre historique — pas
d'un autre serveur (essais faits avec PSM/eBay et Alibaba : leurs plages de
fonctionnement sont trop différentes des nôtres pour être mélangées).

### Régénérer le jeu d'entraînement puis le modèle

```bash
# 1. exporter l'historique réel de srv-002 depuis la base collector-service
#    (colonnes : les 7 métriques + la date), vers data/srv002_full.csv
#    (voir la requête SQL dans retrain_srv002_only.py — table
#     collector_user.BCT_METRIC_SNAPSHOTS)

# 2. réentraîner
cd services/prediction-engine
python retrain_srv002_only.py
```

Le script exclut la journée du 13 août (test de pression mémoire volontaire,
42% de relevés anormaux ce jour-là) et vérifie l'accord modèle/règles sur
toutes les lignes réelles.

## PSM — évaluation scientifique de l'algorithme (séparé)

`notebooks/psm_isolation_forest_evaluation.ipynb` évalue l'**algorithme**
Isolation Forest sur un vrai jeu de données étiqueté (PSM, serveurs eBay) —
c'est une validation de la méthode, **pas** la source d'entraînement du
modèle de production. À télécharger avant d'exécuter le notebook :

```bash
mkdir -p data/psm && cd data/psm
curl -sLO https://raw.githubusercontent.com/eBay/RANSynCoders/main/data/train.csv
curl -sLO https://raw.githubusercontent.com/eBay/RANSynCoders/main/data/test.csv
curl -sLO https://raw.githubusercontent.com/eBay/RANSynCoders/main/data/test_label.csv
```

Source : [eBay/RANSynCoders](https://github.com/eBay/RANSynCoders/tree/main/data)
— 25 métriques anonymisées et normalisées, labels d'anomalie réels sur le jeu de test.
