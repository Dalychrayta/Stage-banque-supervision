# Console de supervision BCT — règles d'interface

Ce fichier décrit la mise en page et les composants à appliquer aux templates
Angular. Les valeurs de couleur ne sont jamais écrites en dur : elles viennent
toutes de `tokens.css`, par `var(--nom-du-jeton)`. Le HTML de référence de
chaque écran est dans `reference/`.

Police : **Plus Jakarta Sans** pour l'interface, **JetBrains Mono** pour toute
donnée machine. Chargées par lien Google Fonts dans `src/index.html` :

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
```

## Ossature de page

La page entière est une grille de deux colonnes avec 20px de marge tout autour :

```
padding: 20px ; grid-template-columns: 236px 1fr ; gap: 20px
```

Le rail de navigation **ne touche aucun bord** : c'est un panneau arrondi
(`--radius-lg`) posé sur le fond, en `--nav-surface`, sur toute la hauteur.
Il reste sombre dans les deux thèmes.

La colonne de droite est une pile verticale : `display: flex; flex-direction:
column; gap: 16px`.

## Rail de navigation

- En-tête : carré de 34px en `--brand`, rayon 11px, icône blanche ; à côté le
  mot « Supervision » en 15px/700.
- Entrées : hauteur 44px, rayon `--radius-md`, icône 18px à gauche.
  Au repos `--nav-ink-muted`. Active : fond `--nav-active-bg`, texte
  `--nav-active-ink`, poids 600.
- Un compteur rouge en pastille sur « Anomalies » quand des incidents sont
  ouverts. Il compte les incidents **non traités**, pas le total.
- En bas, avant le pied : tuile `--nav-inner` avec la disponibilité sur 30
  jours, sa jauge et la cible.
- Pied : « Banque Centrale de Tunisie », 11px, `--nav-ink-muted`.
- Libellés en français. « Auto-réparation », jamais « Auto-Healing ».

## En-tête de vue

Il n'y a **pas** de barre supérieure pleine largeur. À la place, une zone de
64px de haut, transparente :

- à gauche : sur-titre 11px majuscules espacées en `--ink-muted`, puis le titre
  de page en 28px/700, interlettrage −0.02em ;
- à droite, des pilules de 44px de haut : recherche, badge de collecte, bouton
  rond de bascule de thème, avatar en `--brand`.

Le titre de page reprend **exactement** le libellé de l'entrée de navigation.

### Le badge de collecte doit être honnête

Vert « En direct » tant que la dernière donnée reçue date de moins de deux fois
l'intervalle de sondage. Au-delà : fond `--status-warn-bg`, texte
`--status-warn`, point creux, libellé « Reprise… ». Le badge porte aussi
l'heure courante en mono.

Un booléen renvoyé par le serveur ne convient pas : s'il est injoignable, il ne
renvoie rien. Comparer un horodatage de dernière réception côté client.

## Cartes et panneaux

- Fond `--surface-raised`, bordure 1px `--border`, rayon `--radius-lg`.
- Pas d'ombre portée : la hiérarchie vient des filets et des surfaces.
- Padding intérieur 20px.
- Séparateurs internes : `border-top: 1px solid var(--hairline)`.

**Règle qui a déjà cassé la maquette :** tout titre de panneau porte
`flex: none` et `white-space: nowrap`. Sans ça, il se coupe en deux lignes et
écrase la première ligne de données.

### Compteur (KPI)

Hauteur 108px, rayon `--radius-md`. Ligne du haut : libellé 12px
`--ink-muted` + une pastille de contexte ou une icône alignée à droite.
En bas, le chiffre en mono 34px/600, interlettrage −0.02em.

Toujours le dénominateur (`42/45`), jamais un pourcentage seul.
La couleur passe sur **la valeur**, jamais sur la carte ou sa bordure.

## Pastilles d'état

Trois signaux redondants : couleur, forme, libellé écrit.

| État | Jeton | Forme du marqueur |
| --- | --- | --- |
| Opérationnel | `--status-up` sur `--status-up-bg` | point plein |
| Dégradé | `--status-warn` sur `--status-warn-bg` | point cerclé (2px) |
| Hors service | `--status-down` sur `--status-down-bg` | triangle |
| Inconnu | `--status-idle` sur `--status-idle-bg` | point creux (1px) |

Rayon `--radius-pill`, padding `4px 11px`, texte 12px/600. Le marqueur porte
`aria-hidden="true"` : le libellé est déjà dans le DOM.

Un état absent de l'énumération retombe sur **Inconnu**, jamais sur
Opérationnel.

## Tableaux

- Pas de bordures de cellule, pas de zébrage : uniquement des filets
  horizontaux `--hairline`.
- En-tête sans fond : 11px/700, majuscules, interlettrage 0.08em,
  `--ink-muted`.
- Lignes de 56px. `table-layout: fixed`, largeurs fixées par colonne, les
  colonnes de texte en `text-overflow: ellipsis`.
- Colonnes chiffrées alignées à droite, en mono tabulaire.
- La colonne de charge est une mini-jauge (6px, rayon pill, piste
  `--surface-sunken`) suivie de la valeur.
- Tri par défaut : hors service, dégradé, inconnu, opérationnel.
- Une valeur absente s'écrit `—`, jamais `0` ni une case vide.
- Pagination en boutons ronds de 34px, avec « 1 à 10 sur 45 » à gauche.
  Masquée quand il n'y a rien à paginer.

## Filtres

Barre en carte de 72px : recherche en pilule avec la loupe à l'intérieur
(`padding-left: 38px`), puis un segmented control — conteneur
`--surface-sunken` en pilule, padding 3px, option active en
`--surface-raised` et 600. Le bouton d'action principal est à droite, en
pilule `--brand`.

## Liste d'anomalies

Pas de tableau : une liste de lignes de 74px. À gauche un trait vertical de
4px×44px au rayon pill, coloré par la sévérité. Au centre, nom d'hôte en mono
et pastille de sévérité sur la première ligne, cause en 14px/600 sur la
deuxième. À droite, le score du modèle en mono 15px avec son libellé, puis
l'heure.

## Fil d'incident

Grille `16px 60px 1fr`. Marqueur rond de 16px en `inset 0 0 0 5px <couleur>`,
relié par un rail de 2px `--hairline` qui s'arrête à la dernière étape
(`:not(:last-child)`).

Chaque étape déclenchée par la plateforme porte une pastille `auto` en
`--brand-tint`, et dit **quatre choses** : l'action, la cible, la durée, le
résultat. « Relance du service » ne suffit pas ; « Relance de `pacs-gateway` —
tentative 1 sur 3, échec après 8 s » se défend en audit.

Au-dessus du fil, trois tuiles de résumé (`--radius-md`, fond
`--surface-sunken`) : ouvert depuis, tentatives, état du service.

## Boutons

Hauteur 44px, rayon `--radius-pill`, 13px/600.

- Primaire : fond `--brand`, texte `--ink-inverse`. **Un seul par écran.**
- Secondaire : fond `--surface-raised`, bordure `--border`.
- Destructif : bordure et texte `--status-down`, fond transparent. Toute
  action visible sur la production ouvre une confirmation qui **nomme la
  cible** : « Basculer `srv-tcp-02` vers le site de secours ? ».

## États vides

Trois cas, à ne jamais confondre :

- rien à signaler, et c'est une bonne nouvelle → `--status-up`,
  « Aucun incident ouvert », pas d'action ;
- rien n'est configuré → `--status-warn`, « Aucune ressource supervisée » +
  bouton « Déclarer une ressource » ;
- un filtre ne renvoie rien → neutre, + « Réinitialiser les filtres ».

Hauteur minimale 160px, sinon le panneau s'effondre au premier chargement.
Un graphique sans données n'affiche **pas** un axe gradué de 0 à 1 : il montre
un état vide, sinon le lecteur croit que la mesure vaut zéro.

## Rafraîchissement sans saut

- Clé de suivi (`track`) sur le nom d'hôte, jamais sur l'index.
- Hauteurs de ligne et largeurs de colonne fixes.
- Chiffres tabulaires partout.
- Horodatages absolus (`14:32:07`) ; le relatif ne vient qu'en complément.

## Accessibilité

Texte à 4,5:1 minimum, 3:1 pour les bordures de contrôle et les icônes. Focus
visible partout, jamais supprimé. `aria-label` sur tout bouton sans texte,
`aria-current="page"` sur l'entrée de navigation active, `aria-pressed` sur la
bascule de thème.

## La bascule de thème

L'attribut `data-theme` se pose sur `<html>`, pas sur le composant racine :
c'est ce qui fait que les overlays du CDK Angular (modales, menus, infobulles),
attachés au `body`, héritent du thème. Persister le choix dans
`localStorage`, avec `prefers-color-scheme` comme valeur initiale.
