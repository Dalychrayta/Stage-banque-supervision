# Keycloak avec le realm "bct" intégré à l'image.
#
# Pourquoi ne pas simplement monter le fichier depuis le disque, comme avant :
# Jenkins tourne dans un conteneur mais pilote le démon Docker de la machine
# hôte. Quand il lance "docker compose up", c'est l'HÔTE qui exécute le
# montage, et le chemin demandé (/var/jenkins_home/...) n'existe pas chez lui.
# Docker crée alors un dossier vide à cet emplacement, et Keycloak refuse de
# démarrer : "Are you trying to mount a directory onto a file?".
#
# En intégrant le realm à l'image, il n'y a plus rien à monter : le contexte de
# construction est envoyé au démon sous forme d'archive, sans jamais dépendre
# d'un chemin de l'hôte. Le déploiement marche donc depuis n'importe où — poste
# de développement, Jenkins, ou un vrai serveur — et le realm est versionné
# avec le code.
#
# Note sur le volume keycloak-data : il est monté sur /opt/keycloak/data, donc
# au-dessus du dossier d'import. Ce n'est pas un problème — Docker recopie le
# contenu de l'image dans un volume nommé VIDE à sa première utilisation, donc
# un déploiement neuf récupère bien le realm. Sur un volume déjà existant, le
# realm y a de toute façon déjà été importé.
FROM quay.io/keycloak/keycloak:26.0

COPY realm-bct.json /opt/keycloak/data/import/realm-bct.json
