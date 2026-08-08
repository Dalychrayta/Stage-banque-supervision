# Dataset — PSM (Pooled Server Metrics)

Utilisé par `notebooks/psm_isolation_forest_evaluation.ipynb` pour l'évaluation scientifique du
Prediction Engine. Non versionné dans Git (fichiers volumineux, ~100 Mo) — à télécharger avant
d'exécuter le notebook :

```bash
mkdir -p data/psm
cd data/psm
curl -sLO https://raw.githubusercontent.com/eBay/RANSynCoders/main/data/train.csv
curl -sLO https://raw.githubusercontent.com/eBay/RANSynCoders/main/data/test.csv
curl -sLO https://raw.githubusercontent.com/eBay/RANSynCoders/main/data/test_label.csv
```

Source : [eBay/RANSynCoders](https://github.com/eBay/RANSynCoders/tree/main/data) — données réelles de
production (serveurs applicatifs eBay), 25 métriques anonymisées et normalisées, avec labels
d'anomalie réels sur le jeu de test.
