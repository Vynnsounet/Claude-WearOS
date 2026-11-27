# Publier MédiPote

## Construire la release

```bash
./gradlew :medipote:testDebugUnitTest :medipote:lintRelease   # vérifications
./gradlew :medipote:assembleRelease    # APK signé : medipote/build/outputs/apk/release/medipote-release.apk
./gradlew :medipote:bundleRelease      # AAB pour le Play Store : medipote/build/outputs/bundle/release/medipote-release.aab
```

Avant chaque nouvelle version, tester l'APK release sur un téléphone (le debug ne suffit pas :
R8 ne s'applique qu'à la release), et augmenter `versionCode` (entier, +1) et `versionName`
dans `medipote/build.gradle.kts`.

### Signature

La release est signée avec la clé décrite dans `medipote/keystore.properties` :

```properties
storeFile=medipote-release.jks
storePassword=…
keyAlias=medipote
keyPassword=…
```

Ces deux fichiers ne sont **jamais** versionnés (`.gitignore`). En CI, utiliser les
variables d'environnement `MEDIPOTE_STORE_FILE`, `MEDIPOTE_STORE_PASSWORD`,
`MEDIPOTE_KEY_ALIAS` et `MEDIPOTE_KEY_PASSWORD`. Sans clé, l'APK release est produit
non signé.

> **Garde la clé et son mot de passe en lieu sûr** (gestionnaire de mots de passe +
> une copie de sauvegarde). Sans elle, impossible de publier une mise à jour d'un APK
> déjà installé. Sur le Play Store, avec « Signature d'application par Google Play »,
> cette clé sert seulement de clé d'importation et peut être réinitialisée en cas de perte.

### Ce que fait le build release

| Optimisation | Effet |
|---|---|
| R8 (réduction du code et renommage, **sans** l'optimiseur) | APK ~6× plus petit que le debug |
| Suppression des ressources inutilisées | Images et layouts non utilisés retirés |
| Langues limitées au français | Les traductions des bibliothèques ne sont pas embarquées |
| Profil de référence (`baseline-prof.txt` + `profileinstaller`) | Code de l'app compilé à l'installation : premier lancement et défilement plus fluides |
| Fichiers `META-INF` superflus et rapport de dépendances retirés de l'APK | Quelques Ko de moins |
| Numéros de ligne conservés (`mapping.txt`) | Rapports de plantage lisibles |

Résultat : **APK de 3,8 Mo** (debug : 22 Mo).

### Le plantage au lancement des versions 1.0.0 et 1.0.1

Elles se fermaient dès l'ouverture, avant même que l'app ne démarre. Les widgets (Glance)
tiraient **WorkManager 2.7.1**, qui embarque **Room 2.2.5**. La règle R8 de Room 2.2.5
(`-keep class * extends RoomDatabase`) date d'avant le « mode complet » de R8, activé par
défaut dans les versions récentes d'AGP. Dans ce mode, garder une classe ne garde pas son
constructeur : `WorkDatabase_Impl`, créée par réflexion, n'en avait plus, et WorkManager
plantait pendant son initialisation (androidx.startup, avant `Application.onCreate`). La
version debug, sans R8, n'était pas touchée.

Corrections (1.0.2) :
- WorkManager 2.11.2 (Room récent, règles R8 à jour) déclaré explicitement ;
- règle de sécurité dans `proguard-rules.pro` :
  `-keep class * extends androidx.room.RoomDatabase { <init>(); }` ;
- rapport d'erreur installé dès `attachBaseContext`, pour capturer aussi ce type de plantage.

L'optimiseur de R8 reste désactivé (`-dontoptimize`) : APK de 3,8 Mo au lieu de ~2,2 Mo, mais
le dex livré peut être reconverti et exécuté hors appareil pour vérification (voir plus bas),
ce que l'optimiseur empêche. Il pourra être réactivé après des essais sur téléphone.

### 1.35.0 — des prises liées au repas, au réveil, au coucher ; des rappels qui suivent les habitudes

- `DoseTime.anchor` / `offsetMinutes` + `Settings.routine` (réveil, petit-déjeuner, déjeuner, dîner, coucher). `minuteOfDay` garde
  l'heure calculée, donc planification, clés de prise, montre et widgets n'ont pas changé. `Routines.sync` (appliqué dans
  `AppRepository.update`) recale les prises liées quand une heure habituelle change et déplace avec elles les prises déjà données.
- Éditeur : « Selon mes repas » (ancre + écart) ; Paramètres › Rappels : « Mes repas et mon réveil » (réveil habituel proposé
  d'après les nuits, au moins 5 nuits).
- `Habits` : médiane des écarts entre l'heure prévue et l'heure réelle des 3 dernières semaines (au moins 7 prises, écart stable de
  20 min ou plus) → carte sur l'accueil « on décale le rappel ? » ; « Pas maintenant » = silence pendant un mois. Jamais pour les
  plages, les prises liées, les « si besoin » ni la pilule.

### 1.34.0 (téléphone) · 1.17.0 (montre) — l'eau en millilitres

- `DayEntry.drinks` (heure, quantité, boisson chaude) : le total est la somme des boissons. Les verres notés avant restent lus
  à la taille du verre de la personne puis sont repliés en une ligne au premier ajout (`Water.normalized`).
- `WaterSettings.goalMl` (0 = suit les anciens verres × leur taille), `countHotDrinks`. Points par tranche de 250 ml, jusqu'au double
  de l'objectif ; retirer une boisson ne reprend pas les points et ne les fait pas payer deux fois.
- Le bouton « verre » des notifications, widgets et de la montre note un verre à la taille du verre ; l'affichage est en L.
- Montre : `WatchSnapshot.waterMl/goalMl/glassMl` (une ancienne montre continue d'afficher des verres).

### 1.33.0 — Ma soirée : ce qui change les nuits

- `SleepFactors` : une trentaine de facteurs rangés par thème (boissons, repas, soirée, autour du lit, plus personnel), avec
  précisions (café après 16 h), choix uniques (quantité, heure du repas), texte libre (« quelle substance ? »), facteurs à soi.
  Les anciennes étiquettes des nuits sont reprises. Stockage : `AppState.evenings` (soirée → facteurs), lu avec la nuit suivante.
- Saisie : fiche de nuit (section « Ma soirée ») et carte « 🌆 Ma soirée » de la page Sommeil, sans attendre le matin.
- Facteurs repérés sans saisie : humeur, symptômes, eau, prise tardive, phase du cycle, rapport noté dans le journal du cycle.
- Analyse prudente (`SleepFactors.effects`) : nuits avec / sans, au moins 5 de chaque côté, écart d'au moins 30 min de sommeil,
  10 min d'endormissement ou 0,5 point de note ; trois mois de nuits ; jamais présenté comme une cause.
- Confidentialité : substances et rapport sexuel cachés tant qu'ils ne sont pas activés ; jamais dans le rapport pour le médecin
  sauf l'option « Sommeil : facteurs plus personnels » (décochée par défaut).

### 1.32.0 (téléphone) · 1.16.0 (montre) — une plage horaire par prise

- `DoseTime.untilMinute` / `remindEnd` : une prise peut être « à heure fixe » (comme avant, sans rien changer aux sauvegardes
  existantes) ou « dans une plage » (début–fin). La clé de la prise reste celle de l'heure de début.
- Toute la plage compte « à l'heure » (`Rules.statusFor(début, fin, prise)`) ; le retard se mesure depuis la fin ; le délai
  de rattrapage part de la fin.
- Rappel au début de la plage (`DOSE`), puis, si la personne l'a choisi, un seul rappel 30 min avant la fin
  (`AlarmKind.WINDOW_END`) tant que la prise n'est pas faite. Pas de relance « 30 min après » pour les plages.
- Les prises « si besoin » n'ont pas de plage. Le voyage décale toute la plage ; l'écart entre traitements prend le pire cas.
- Montre : `WatchDose.until`, rappel `WINDOW_END`, état « à prendre » jusqu'à la fin de la plage (montre 1.16.0 ; une ancienne
  montre ignore la plage et sonne au début).

### 1.31.0 — où ça fait mal, précautions, scan de boîte

- Schéma du corps (face / dos) sous chaque symptôme noté, zones dans les statistiques du mois et le rapport médecin.
- Précautions par médicament et écarts entre traitements (`Precautions`), alerte en tête de la liste.
- Scan de boîte : `play-services-code-scanner` 16.1.0 (scanner exécuté par Google Play services, sans permission d'appli) +
  `assets/cip.tsv.gz` (CIP13 → nom, BDPM, Licence Ouverte 2.0) à régénérer avec `python3 medipote/tools/build_cip_directory.py`.
  Vérifier après build que l'APK ne déclare toujours **pas** `INTERNET` (voir ci-dessous) ; non testé sur un vrai téléphone.

### 1.28.0 — Comprendre

- Nouvelle carte « 🔎 Comprendre » (Suivi) et page « Chez toi, ça peut venir de… » sur chaque symptôme : ce qui peut l'expliquer
  dans ton contexte, avec les chiffres de tes notes (sommeil, cycle, eau, traitement récent, prises manquées, fièvre, tension,
  maladie, conditions suivies, symptômes qui reviennent ensemble ou à une phase du cycle).
- « Faut-il consulter ? » : des règles prudentes (symptôme fort plusieurs jours, presque tous les jours, depuis deux semaines, qui
  s'aggrave ; plusieurs symptômes à la fois ; tension, pouls, température, saturation, poids ; nuits blanches ou très courtes ;
  règles absentes ou cycles hors des repères ; moral bas ; prises souvent manquées). Trois niveaux : à évoquer au prochain
  rendez-vous, prends rendez-vous, appelle le 15 (boutons 15 et 112). Un bandeau sur l'accueil prévient (désactivable dans
  Paramètres › Ce qui m'accompagne › Comprendre) ; « Je l'ai vu » met de côté une semaine, jamais une urgence.
- Conseils pour ton cycle (selon la phase et ton journal) et pour ton sommeil (selon tes nuits), sans médicament.
- Ce n'est pas une IA générative : des règles écrites à la main, testées (dont un test qui interdit tout nom de médicament, de
  diagnostic ou de consigne sur le traitement), exécutées sur le téléphone, sans Internet. Les seuils sont prudents et à faire
  relire par un professionnel de santé.

### 1.27.2 — les nuits blanches, dites dans les statistiques

- Une nuit blanche compte toujours pour 0 h dans la moyenne de sommeil, et n'atteint jamais l'objectif ; elle est maintenant
  nommée : « Nuits blanches : N, sur X nuits notées » dans Suivi › Sommeil et Statistiques › Sommeil, et dans le rapport pour le médecin.

### 1.27.1 — la nuit blanche

- Sommeil : « 🌙 Nuit blanche, je n'ai pas dormi » en un toucher, ou le réglage « Nuit blanche » dans l'éditeur d'une nuit.
  Elle compte pour 0 h de sommeil, sans fausser l'endormissement, l'efficacité ni les horaires habituels.
- Elle se modifie si le sommeil est finalement venu : « Finalement, je me suis endormi·e plus tard… » demande l'heure
  d'endormissement, la nuit devient une nuit normale (l'attente compte comme temps pour s'endormir) et le réveil se règle.

### 1.27.0 — se mettre en pause, retrouver ses jours, changer de téléphone

- Pause des rappels (Paramètres › Rappels) : jusqu'à demain matin, 3 jours ou 1 semaine. L'eau, le stock, les rituels, le récap
  et le sommeil se taisent ; les prises et les rendez-vous sonnent toujours, sauf si on ajoute « Aussi les rappels de prises »
  (désactivé par défaut, avec un avertissement). Un bandeau sur l'accueil rappelle la pause ; la montre suit.
- « 💡 Une idée, un souci ? » (Paramètres › Aide, ou la loupe) : une liste locale, avec l'écran où l'on était, qu'on peut partager
  en texte. Rien n'est envoyé par l'appli.
- Calendrier (Suivi) : le mois d'un coup d'œil, avec des points pour les prises, l'humeur et les symptômes ; un jour sans point
  est juste un jour sans note. Toucher un jour l'ouvre.
- « ✅ Ordonnance renouvelée » sur la fiche d'une ordonnance : les rappels s'arrêtent, +5 points une seule fois.
- Sauvegarde : guide « Je change de téléphone » en cinq étapes et « Tester une sauvegarde » (le fichier est ouvert et décrit,
  sans rien changer).

### 1.26.1 — un petit tour plus aéré

- Le petit tour passe de sept à cinq pages : une idée et quelques mots par page, plus d'espace.

### 1.26.0 — un accueil à ta mesure

- « Accueil du moment » (Paramètres › Mon accueil, facultatif) : le matin, la journée et le soir ne mettent pas en avant les
  mêmes blocs ; le reste attend sous « Voir plus », rien n'est masqué pour de bon.
- Raccourcis : un appui long sur une carte d'un onglet l'épingle sur l'accueil (quatre au plus) ; un appui long sur un
  raccourci le retire. Gérables aussi dans « Mes cartes ».
- Rangement selon l'usage : les cartes ouvertes le plus souvent passent devant et, après deux semaines, une carte jamais
  ouverte est proposée au rangement (« Ranger » ou « Garder », jamais sans demander). Décompte local, désactivable.
- Paramètres en neuf groupes qui s'ouvrent d'un toucher, un seul à la fois ; la recherche ouvre le bon groupe.
- Mode sobre (Paramètres › Jeu et récompenses) : plus de points, de série, de badges ni de boutique à l'écran ; le suivi
  est le même et les points continuent d'être comptés sans bruit.

### 1.25.0 — tout trouver tout de suite

- Recherche dans l'appli (loupe + cœur dans chaque barre) avec les mots de tous les jours, tes traitements, rendez-vous,
  documents, réglages et quelques actions ; elle dit où vit chaque chose et l'ouvre sur la bonne carte.
- Une seule place par chose : « Ça ne va pas » et « Respirer » ne sont plus répétés dans « + Noter » et Mieux-être ;
  « Jour par jour » est fusionné avec le journal. Chaque carte ouverte montre l'onglet où l'on est.
- Petit tour de la première fois (sept pages, facultatif, à revoir dans Paramètres › Aide) avec « Qu'est-ce qui compte pour toi ? »
  qui range d'emblée les cartes dont on n'a pas besoin.
- Texte de bienvenue adouci (plus de « il tombe malade »). Correctif : finir la création du profil ne renvoie plus à l'accueil
  avant le tour.

### 1.24.0 — l'aide au sommeil et des points pour prendre soin de soi

- « 🌙 Nuit difficile ? » dans Sommeil : écran sombre et chaud, deux cas (m'endormir, me rendormir), quatre idées,
  relaxation guidée, règle des 20 minutes, « Ma recette pour me rendormir », « Pour mieux dormir les prochains
  soirs », petit mot du soir facultatif (hors heures calmes), raccourci sur l'icône. Sans médicament ni diagnostic.
- Points : mesure du jour +2, symptôme noté du jour +2, rendez-vous préparé +5 / honoré +10, traitement racheté +5,
  cadeau-surprise après 7 jours de suite (+25), trois défis de la semaine en plus. Jamais retirés ; rien pour les
  prises « si besoin », le cycle ou les siestes. « Malus » supprimé.
- Boutique : souhait de la semaine avec barre de progression, et quatre objets cosy (bonnet de Noël, plaid, tasse de
  tisane, lanterne).
- Correctif : le choix des défis de la semaine reste varié quel que soit le nombre de défis possibles.
- Pas encore : un point pour une ordonnance renouvelée (l'appli n'a pas d'action « renouvelée » à récompenser).

### 1.23.0 — des heures différentes selon les jours

- Chaque heure de prise d'un traitement a ses propres jours (10:00 en semaine et 12:00 le week-end, 10:00
  sauf le mercredi, seulement lundi et vendredi…). Rappels, accueil, montre, stock, statistiques et rapport
  pour le médecin suivent ; changer les jours d'une heure ne réécrit pas le passé (testé). Les traitements
  existants ne changent pas. Montre : rien à réinstaller, elle suit ce que le téléphone lui envoie.

### 1.22.2

- Chaque sensation de « Ce que je ressens » a maintenant 4 conseils pour la faire passer (3 affichés, le 4e
  sous « Plus d'idées »), et chaque symptôme du suivi a au moins 4 conseils.
- Rapport PDF pour le médecin : la partie « Profil » ajoute les conditions suivies dans l'appli (« Ce qui
  m'accompagne ») quand le suivi est activé. Les symptômes notés depuis « Ce que je ressens » y figuraient déjà,
  puisqu'ils sont ajoutés au suivi des symptômes.

### 1.22.1

- L'écran « Ça ne va pas » n'a plus la carte « Gorge nouée ? » : ses conseils (mâchoire, épaules, petites
  gorgées d'eau, une main sur la gorge) sont dans « Ce que je ressens » › Moral bas › « Gorge nouée, envie de
  pleurer ».

### 1.22.0 — essayer rapporte des points, écran plus léger

- « Ce que je ressens » et les conseils d'un symptôme : un petit « ✓ J'ai essayé » par carte, qui rapporte
  quelques points (5, trois petites victoires par jour au plus, partagées avec les missions). Jamais retirés,
  rien ne montre ce qui n'a pas été essayé.
- Écran allégé : trois cartes d'abord (une ouverte à la fois), deux idées par carte et « Plus d'idées ».

### 1.21.0 — « Ce que je ressens »

- Dans « Ça ne va pas » : un guide des signes de l'anxiété, de l'angoisse et du moral bas, dans le corps et dans
  la tête (poids dans la poitrine, fatigue extrême, mal à se lever, envie de rien faire, poitrine serrée, cœur qui
  bat vite…). Gestes à essayer tout de suite, pourquoi ça arrive, quand en parler (15, 3114), boutons vers les
  outils, et « Les noter dans mes symptômes ». Hors ligne, sans diagnostic. Contenu à faire relire par un·e
  professionnel·le de santé.

### 1.20.0 (téléphone) · 1.15.0 (montre) — widget, récap, montre

- Widget « Comment je me sens » : cinq visages pour noter l'humeur d'un geste, raccourcis « Ça ne va pas » et
  « Un symptôme ».
- Récap de la semaine : cartes « Tes symptômes » et « Tes petites victoires », mot du compagnon adapté.
- Montre 1.15.0 : « 🌡️ Symptômes » et « 💛 Journée douce » (visibles seulement si activés sur le téléphone).
  Le protocole phone ↔ montre garde ses anciens champs : un téléphone plus ancien ou une montre plus
  ancienne fonctionnent toujours (testé). Installer la montre après le téléphone : voir plus bas.
- Aucune permission ajoutée, ni sur le téléphone ni sur la montre.

### 1.19.0 — rendez-vous, journal relié aux symptômes, heures calmes

- « Préparer mon rendez-vous » : questions à l'avance + résumé des 30 derniers jours (copier, partager,
  ou rapport PDF). Rapport PDF : nouvelle partie « Symptômes suivis » et cycle plus détaillé.
- Journal et symptômes reliés : la force des symptômes suivis se note aussi depuis le journal d'un jour, un
  symptôme coché au journal est rappelé dans Suivi › Symptômes, « Ma journée » les montre.
- Accueil simple / accueil complet en un geste (Paramètres › Mon accueil).
- Heures calmes : rappels doux (eau, stock, rituels, récap, « bien dormi ? ») retenus la nuit ; les prises,
  rendez-vous et minuteurs ne bougent jamais.
- Raccourcis sur l'icône de l'appli (appui long).
- Sauvegardes anciennes : chargées sans rien changer (champs ajoutés avec des valeurs par défaut, testé).

### 1.18.1 — mode sombre mieux accordé

- Mode sombre : les couleurs d'accentuation « générées » (Forêt, Ciel, Indigo, Prune, Cerise, Mandarine,
  Tournesol, Lagon, Ardoise, Cacao, Bonbon, Olive et la couleur libre) étaient trop vives, presque
  fluo : elles sont maintenant des pastels doux, comme Menthe ou Océan.
- Les gris du fond penchent désormais vers la teinte de l'accent (au lieu de rester verdâtres pour
  tous), et la troisième couleur n'est plus un violet fixe mais une voisine de l'accent.
  Menthe, la couleur d'origine, ne change pas. Le mode clair non plus.

### 1.18.0 — les statistiques du cycle

- Nouvel onglet « 🩸 Cycle » dans les statistiques (si le cycle est activé) : durée moyenne et écart entre
  les cycles, durée des règles, courbe des cycles, flux, symptômes fréquents, moral et forme par phase,
  protections et médicaments, et ce que l'appli remarque (moral plus bas ou symptôme plus fort dans une
  phase). Les cycles hors de 21 à 35 jours, répétés, invitent à en parler à un médecin. Le cycle en cours
  et le cycle moyen figurent aussi dans la vue d'ensemble.
- Calculs sur le téléphone, aucune permission ajoutée.

### 1.17.0 — des statistiques par sujet

- Statistiques en sous-onglets : Vue d'ensemble, Traitements, Sommeil, Symptômes (si activés), Bien-être.
  Même période (7, 30, 90 jours) pour tous. Nouveau : statistiques de chaque symptôme (force, jours,
  comparaison avec avant, courbe), onglet Sommeil (durée nuit par nuit, siestes), moral au fil du temps,
  petites victoires. Les liens symptômes / sommeil restent des observations, jamais un diagnostic.
- Aucune permission ajoutée, tout reste sur le téléphone.

### 1.16.0 — journée douce, symptômes, onglets en cartes

- Chaque onglet s'ouvre sur une page de cartes aérées avec un aperçu en direct. Onglets : Accueil,
  Soins, Suivi, **Mieux-être** (nouveau) et **Moi** (Bilan et Compagnon réunis). « Mes cartes »
  (Paramètres, ou en bas de chaque page) masque ce qu'on n'utilise pas ; rien n'est supprimé.
- Nouveau, facultatif et masqué par défaut : « Ce qui m'accompagne » (conditions, symptômes à suivre,
  missions perso), suivi des symptômes (1 à 5) avec conseils doux et quand consulter, « Journée
  douce » (missions selon l'énergie, découpage de tâches en étapes, chrono de 5 minutes), carte
  d'accueil les jours lourds, entrées dans « + Noter » et dans « Ça ne va pas ».
- Points : gagnés seulement, jamais retirés ; trois petites victoires payent par jour ; aucun effet sur
  les séries. Tout hors ligne, aucune permission ajoutée.
- Migration : une sauvegarde ancienne se charge avec tout désactivé (testé).
- Le contenu de santé (fiches, conseils) est à faire relire par un·e professionnel·le avant diffusion large.

### 1.15.1

- La carte de santé du compagnon (nom, barre de santé, stade) passe en haut à droite de
  l'illustration et devient plus petite, pour laisser voir la fenêtre de la chambre.

### 1.15.0 — saisons et fêtes

- Le monde du compagnon change avec l'année : feuilles qui tombent à l'automne, neige l'hiver,
  pétales au printemps, lumière dorée et lucioles l'été, par-dessus le fond et l'heure (dans la
  chambre, on voit la saison par la fenêtre). Sur le widget et sur la montre aussi (l'image du
  compagnon est la même).
- Une phrase du jour sur trois suit la saison ; une très légère teinte de saison sur les fonds de
  l'appli (jamais la couleur d'accent, contraste du texte testé).
- Fêtes (à cocher) : Nouvel An, Saint-Valentin, Pâques, Halloween, Noël, anniversaire du compagnon
  (le premier jour d'utilisation) et le tien (date facultative, jour et mois seulement). Petit
  décor, et le compagnon te souhaite la fête. Pas de fête des mères ni des pères.
- Jamais de décor ni de vœux de fête les jours où tu notes une humeur « très mal ».
- Paramètres › Personnaliser › « Saisons et fêtes » : interrupteur général, teinte, hémisphère sud,
  choix des fêtes, date d'anniversaire.

### 1.14.0 — « Ça ne va pas »

- Un écran de soutien pour les moments très difficiles, sans points ni fête, accessible en deux
  gestes : petit cœur en haut de l'accueil (masquable), « 🤗 Ça ne va pas » dans « + Noter », et
  une proposition douce quand on note l'humeur « très mal » (désactivable).
- Dedans : **le 3114** en haut (appel en un geste, jour et nuit, gratuit) puis le 15, le 112 et
  le numéro de son médecin ou psy ; **Respirer** (cercle guidé, expiration plus longue, trois
  rythmes, vibration douce facultative) ; **Me poser** (5-4-3-2-1) ; **Vider ma tête** (écrire sans
  filtre, rien n'est gardé si on ne le demande pas) ; **Ma boîte à réconfort** (un message à soi, ce
  qui fait du bien) ; **Mon plan** (signes, ce qui aide, qui appeler, ce qui compte) ; « Gorge
  nouée ? » (mâchoire, épaules, eau) ; jusqu'à trois personnes de confiance avec appel et message
  tout prêt ; « Comment tu te sens ? » et « Me redemander dans 2 h » (une notification douce).
- Les appels ouvrent le numéroteur avec le numéro prêt : rien n'est composé sans appui sur
  « appeler ». Aucun accès Internet, tout reste sur le téléphone (permission ajoutée : VIBRATE).
- Le compagnon ne parle plus de séries ni de points les jours d'humeur « très mal » : juste de
  la compagnie. « Gorge nouée » ajouté aux symptômes du journal.
- Réglages : Paramètres › Soutien (personnes de confiance, médecin ou psy, cœur, proposition,
  vibrations).
- Montre : « 🤗 Ça ne va pas » : cercle de respiration lent avec un petit tic au poignet, et le 3114.
- Les emojis récents (🫁, 🫂, 🛟) sont remplacés par des plus anciens, lisibles sur tous les téléphones.

### 1.13.0 — les siestes

- Suivi › Sommeil : « 😴 Je m'endors » (chronomètre) ou « Noter une sieste ». Le chronomètre
  reste visible sur la page Sommeil et sur l'accueil ; « Je me réveille » note la sieste avec la
  durée écoulée (un minuteur oublié des heures demande de corriger la durée, une pression de
  quelques secondes ne note rien).
- Écran de saisie court : durée (− / +, raccourcis 15 min à 2 h), début, « Et après ? »
  (en forme / pareil / dans le brouillard) ; jour et note repliés. Jusqu'à 4 h, au-delà on propose
  de noter une nuit.
- Les siestes sont **comptées à part** : les chiffres de la nuit (moyenne, réveils, efficacité,
  séries, badges, défis) ne changent jamais. Ils s'ajoutent : carte « Siestes » (nombre, durée
  moyenne, total), « Nuit + siestes : … par jour », liste « Voir mes siestes », la sieste du jour
  sous la nuit, « +… de sieste » sur la nuit du jour.
- Paramètres › Sommeil : « Compter les siestes dans mon objectif » (activé) ; il ne touche que
  l'objectif.
- Aussi dans : bouton « + Noter » (« Une sieste », « Terminer ma sieste »), Ma journée, rapport
  pour le médecin, compagnon (un mot après une sieste, plus de « une petite sieste ? » ce jour-là).
- Aucun point, badge, défi ni rappel pour les siestes.
- Montre : « 😴 Sieste » (chronomètre « Je m'endors » / « Je me réveille », ou une sieste qui vient
  de finir : 20 min, 30 min, 45 min, 1 h).

### 1.12.0

- Montre : « 💨 Noter ma saturation » (70 à 100 %, boutons − et +). La valeur arrive dans
  Suivi › Mesures du téléphone (« Depuis la montre »), le suivi de la saturation s'active tout
  seul, et la montre affiche la dernière valeur. Message doux si elle est sous 95 % (comme sur
  le téléphone). La montre ne la **mesure pas** elle-même : ni Android ni Health Services
  (1.1.0) n'ouvrent le capteur de saturation aux applis tierces ; la valeur se lit sur un
  oxymètre ou dans l'appli santé de la montre.

### 1.11.5

- Sommeil : avec une couleur d'appli violette (ou toute couleur proche de la seconde teinte du
  thème), les phases d'éveil se confondaient avec le sommeil. La couleur d'éveil est maintenant
  choisie pour toujours bien trancher sur celle du sommeil (ambre, orange, bleu clair… selon
  l'accent), et un fin liseré sépare chaque éveil du sommeil autour. Un test vérifie le contraste
  pour toutes les couleurs proposées et des couleurs libres claires et sombres.

### 1.11.4

- Accueil : le compagnon passe juste sous la série et la phrase du jour, puis viennent les
  alertes (« Pense à racheter »…) et la prochaine prise. L'ordre déjà enregistré est mis à jour
  une seule fois (le reste de ton ordre est gardé) ; Paramètres › Mon accueil permet toujours de
  tout réorganiser.

### 1.11.3

- Écran de la série : le titre (« Ça continue ! »…) était noir sur le fond sombre du thème
  sombre, donc presque invisible. Il est maintenant clair, plus grand et en gras, comme le texte
  qui l'accompagne ; le test vérifie son contraste dans les conditions de l'appli.

### 1.11.2

- Série quotidienne : le jour change à **4 h du matin** (avant, à minuit). Ouvrir l'appli à 1 h
  ne compte plus le nouveau jour et ne gâche plus l'écran de la série du matin : il s'affiche à
  la première ouverture à partir de 4 h. Seule la série est concernée (prises, journal, eau…
  restent sur la journée normale).

### 1.11.1

- Écran de la série : conservé côté appli jusqu'à ce qu'on le ferme (plus de risque de le perdre
  si l'appli n'écoutait pas encore au moment de l'ouverture ou si l'écran pivote), et les autres
  messages envoyés trop tôt (badges…) attendent aussi leur tour.
- Personnaliser › « 🔥 Revoir l'animation de la série » : revoir l'écran à la demande, sans rien
  compter ni gagner.

### 1.11.0 — compagnon Wear OS

- Nouvelle appli montre (`medipote-wear`, même identifiant et même signature que le téléphone) :
  rappels qui vibrent au poignet, validation des prises, eau, « si besoin », protection, pouls.
- Téléphone : envoi de l'état à la montre, réception des actions (enregistrées à l'heure du
  geste), section « Montre » dans les Paramètres, rappels gardés sur le téléphone quand la
  montre sonne elle-même. Dépendance ajoutée : `play-services-wearable` (toujours aucune
  permission Internet).
- Correctif : le rappel de relance (« pas encore pris ») était programmé à l'heure de la prise
  au lieu de 30 minutes après.

#### Construire et installer la montre

```bash
./gradlew :medipote-wear:testDebugUnitTest :medipote-wear:lintRelease
./gradlew :medipote-wear:assembleRelease   # medipote-wear/build/outputs/apk/release/medipote-wear-release.apk
./gradlew :medipote-wear:bundleRelease     # AAB à joindre à la même fiche Play Store (format « Wear OS »)
```

Hors Play Store, l'APK s'installe sur la montre par ADB sans fil :

1. Sur la montre : Paramètres › Système › À propos › Versions, toucher 7 fois « Numéro de
   build » ; puis Paramètres › Options pour les développeurs › « Débogage ADB » et « Débogage
   sans fil » (montre et ordinateur sur le même Wi-Fi). « Associer un appareil » affiche une
   adresse et un code.
2. Sur l'ordinateur : `adb pair ADRESSE:PORT` (saisir le code), puis `adb connect ADRESSE:PORT`
   (le port affiché sur l'écran « Débogage sans fil »), puis
   `adb install MediPote-Montre-1.11.0.apk`.
3. Mettre à jour l'appli du téléphone (1.11.0 ou plus), ouvrir MédiPote sur la montre et
   accepter les notifications : la montre se relie toute seule (Paramètres › Montre sur le
   téléphone affiche « Montre reliée »).

Sans ordinateur, des applis comme « Bugjaeger » (sur le téléphone) font la même chose.
Les deux applis doivent être signées avec la même clé, sinon elles ne se parlent pas.

### 1.10.0

- Sous-onglets : un emoji avec un petit mot dessous ; nouveaux emojis 🔁 Pilule, 🩸 Cycle,
  📋 Par jour, 📆 Année. Le jardin et les fleurs sont retirés.
- Traitements : cartes compactes (nom, horaires, stock seulement s'il est bas), détail au toucher,
  crayon pour modifier ; « Noter » toujours visible pour les médicaments si besoin.
- Journal : historique en lignes (date et visages), détail au toucher.
- Sommeil : dernière nuit, moyennes, ressenti et graphique d'abord ; « Plus de détails » et
  « Voir mes nuits précédentes » repliés.
- Cycle : cartes protection et médicaments en haut pendant les règles, en bas sinon.
- Badges : collection gagnée d'abord, les badges à débloquer repliés (les plus proches d'abord).
- Couleur vive réservée aux boutons d'action, plus d'espace entre les blocs.

### 1.9.0

- Eau : chaque verre rapporte des points même après l'objectif, jusqu'au double de l'objectif.
- Question du jour retirée (accueil, journal, rituel du soir) ; le badge « Belles pensées » et
  le défi correspondant comptent désormais les notes du journal.
- Phrase du jour : une petite phrase douce ou motivante, différente chaque jour, en haut de
  l'accueil (bloc masquable dans Mon accueil). Remplace la phrase personnelle des paramètres.
- Le fond du compagnon suit l'heure : aube (6–8 h), jour (8–16 h), coucher de soleil
  (16–19 h), nuit (19–6 h) avec lune, étoiles et lucioles. Les autres fonds sont teintés.

### 1.8.0 — une appli plus aérée

- Bouton « + Noter » sur l'accueil : humeur, médicament si besoin, nuit, verre d'eau,
  protection, mesure, journal, rendez-vous, en deux gestes.
- Accueil : la prochaine prise en grand en haut (« Donner à … »), les prises faites repliées
  en une ligne, alertes dans des cartes calmes (couleur sur l'icône seulement), série, points,
  badges et défis sur une ligne.
- Chaque bloc s'affiche quand il sert : protection pendant les règles, rituel le matin et le
  soir, récap le dimanche soir et le lundi, humeur repliée une fois remplie, si besoin après
  une prise des dernières 24 h, eau si suivie, rendez-vous dans les 3 jours.
- Page « Mon accueil » (Paramètres) : afficher, masquer et ordonner chaque bloc.
- Explications repliées (« ⓘ À savoir », petits ⓘ, barème des points replié).
- Formulaires : l'essentiel d'abord, le reste sous « Plus d'options » (traitement, nuit,
  journal, rendez-vous, mesure) ; fiche du médicament repliée.

### 1.7.1

- La carte « médicaments pour les règles » ne montre plus tous les médicaments « si besoin »
  (un anxiolytique n'y a rien à faire) : seulement les antidouleurs courants et ceux qu'on
  choisit. Bouton ✕ pour retirer un médicament de la liste (il reste dans les traitements).
- « Autre médicament » : en choisir un parmi ses médicaments « si besoin », ou en créer un
  nouveau (nom avec suggestions du guide, dosage facultatif).

### 1.7.0

- Minuteur de protection périodique (serviette, serviette lavable, tampon, coupe, disque,
  culotte de règles) : rappel réglable par protection, durée maximale, notification avec
  « J'ai changé ✓ » et « J'ai retiré », rappel du risque de choc toxique pour les protections
  internes. Carte dans Suivi › Cycle et sur l'accueil tant qu'un minuteur tourne.
- Médicaments pendant les règles : « Noter » en deux gestes, motif « Règles douloureuses » et
  contexte « Règles » déjà remplis ; ajout rapide de paracétamol, ibuprofène, phloroglucinol.

### 1.6.0

- Navigation réorganisée en cinq espaces : Accueil, Soins (traitements, pilule, agenda,
  carnet), Suivi (journal, sommeil, mesures, cycle), Bilan (stats, jour par jour, année),
  Compagnon (boutique, badges et défis, jardin).
- Sous-onglets en emojis (plus jamais de mots coupés), nom de l'onglet ouvert sous le titre.
- Accueil plus aéré : plus d'espace entre les blocs, défis et jardin résumés en une ligne.

### 1.5.0

- Badges (21, dont 6 avec un objet exclusif dessiné), défis de la semaine, joker de série.
- Rituels du matin et du soir (avec rappels facultatifs), humeur en un geste, question du jour.
- Ma journée, année en pixels, récap de la semaine (notification le dimanche à 19 h), jardin.
- Le compagnon réagit à la nuit et au journal.
- Correctif : avec 5 onglets dans Suivi, les libellés étaient coupés (« Somme », « Mesure ») :
  la barre d'onglets défile désormais.

### 1.4.0

- Prises oubliées : historique des 14 derniers jours, « Je l'ai pris » avec l'heure réelle,
  « Annuler » pour une prise notée par erreur ; bandeau le lendemain.
- Personnalisation : 18 couleurs + couleur libre (tons générés avec contraste vérifié, testé
  pour chaque couleur), fond teinté, noir pur, police des titres, taille du texte, coins,
  emoji, phrase du jour, ordre et affichage des blocs de l'accueil, animations.
- Animation de la série quotidienne à la première ouverture du jour.
- L'IMC n'est plus calculé ni affiché nulle part (profil, mesures, rapport).

### 1.3.1

- Sommeil : la nuit se note aussi « reposante » et « agréable » (en plus de « bonne »), d'un
  visage sur 5 ; moyennes, analyses et rapport PDF.

### 1.3.0

- Mesures de santé (tension, pouls, glycémie, poids, température, saturation), chaque type
  affiché ou non selon les paramètres ; repères indicatifs, courbes, statistiques.
- Sauvegarde et restauration (fichier `.medipote`, mot de passe facultatif, AES-256-GCM +
  PBKDF2), sauvegarde automatique hebdomadaire dans un dossier choisi.
- Boutons « Pris ✓ », « +10 min », « +30 min » dans les rappels ; « J'ai bu un verre ».
- Rapport PDF pour le médecin.
- Verrouillage par empreinte, visage ou code du téléphone (androidx.biometric ;
  androidx.fragment épinglé en 1.8.9 car biometric 1.1.0 tire fragment 1.2.5, dont la
  FragmentActivity refuse les codes de requête des API actuelles : le test d'intégration
  plantait au lancement d'un sélecteur).
- Ordonnances : date de validité et rappel de renouvellement.
- Posologie : tous les N jours, pendant N jours, paliers de dose.
- Compagnon qui grandit avec les prises à l'heure ; widget Sommeil.
- Correctif : le bandeau de stock de l'accueil affichait « null j » pour un médicament
  « si besoin ».

### 1.2.2

- À l'heure = de 30 min avant à 30 min après l'heure prévue (avant : de 1 h avant à 30 min
  après). « Donner » apparaît 30 min avant ; plus tôt, « Déjà pris ? ».
- Notifications en pop-up avec son et vibration pour tous les rappels : nouveaux canaux de
  priorité haute (Android interdit de relever la priorité d'un canal existant, les anciens sont
  supprimés), contenu masqué sur l'écran verrouillé, alerte si un canal est devenu discret.

### 1.2.1

- Changer l'heure d'une prise ne la fait plus redonner : les prises déjà données (aujourd'hui
  et les jours passés) suivent leur nouvelle heure, et l'historique ne montre pas d'oubli.
- Une heure de prise ajoutée à un traitement n'est due qu'à partir de son ajout (pas
  d'oublis rétroactifs, le compagnon n'est pas affaibli).

### 1.2.0

- Suivi du sommeil : coucher, endormissement, réveils dans la nuit (heure, durée, cause),
  réveil, qualité, habitudes ; frise de la nuit, statistiques et analyses, rappel du matin
  facultatif, onglet Suivi › Sommeil et résumé dans Stats.
- Correctif : la notification de stock bas affichait « (null j) » pour un médicament
  « si besoin » ; elle indique maintenant la quantité restante.

### 1.1.0

- Médicaments « si besoin » : saisie des prises (motif, contexte, intensité, effet), limites
  avec avertissement, rappel « est-ce que ça a fait effet ? » une heure après, statistiques dédiées.
- Statistiques refaites : période 7/30/90 j, comparaison, écart moyen, analyses, évolution sur
  12 semaines, calendrier, par moment, par jour de la semaine, bien-être et hydratation.
- Données : champs ajoutés avec valeurs par défaut, les données des versions précédentes se
  relisent sans migration.

### Vérifications faites sur l'APK release

- Signature v2 valide (`apksigner verify`), application non débogable.
- Aucune permission Internet : l'app ne peut rien envoyer.
- Permissions : uniquement notifications, alarmes exactes, redémarrage et `WAKE_LOCK`
  (WorkManager). `ACCESS_NETWORK_STATE` et `FOREGROUND_SERVICE`, ajoutées par
  WorkManager mais inutiles, sont retirées.
- R8 conserve les sérialiseurs des données, l'activité, les récepteurs, les widgets,
  leurs actions et le constructeur du ViewModel (instanciés par réflexion).

- Le dex de l'APK, reconverti en bytecode Java (dex2jar), passe les initialiseurs de démarrage
  sous Robolectric : c'est ce test qui a reproduit le plantage des 1.0.0/1.0.1.
- Le code release (R8 rejoué avec la configuration exacte du build, en sortie bytecode Java)
  passe les initialiseurs puis affiche l'écran d'accueil avec Android 14, 15 et 16.

Ce qui n'a pas pu être fait ici faute d'émulateur : lancer l'APK lui-même sur un appareil.
À tester avant publication : premier lancement, ajout d'un traitement, rappel reçu,
widget posé, redémarrage du téléphone (les rappels doivent revenir).

## Rapport d'erreur

Si l'app plante, le rapport (modèle, version d'Android, détail de l'erreur) est gardé sur le
téléphone ; au lancement suivant, une boîte de dialogue propose de le partager (SMS, e-mail…).
Rien n'est envoyé automatiquement : l'app n'a pas accès à Internet. Pour lire les noms de
classes du rapport : `retrace` du SDK avec le `mapping.txt` de la version concernée.

## Si l'app ne s'installe pas ou ne s'ouvre pas

- « Application non installée » / conflit de package : désinstaller d'abord toute version
  précédente signée avec une autre clé (les APK de test d'avant la 1.0.0).
- Samsung : **Paramètres → Sécurité et confidentialité → Auto Blocker** bloque les
  installations hors Play Store ; le désactiver le temps de l'installation.
- Play Protect peut avertir pour une app installée hors Play Store : « Plus de détails →
  Installer quand même ».
- Installer le fichier **.apk** ; le fichier .aab est réservé au Play Store et ne s'ouvre pas
  sur un téléphone.

## Installer à la place de la version de test

L'APK release est signé avec une autre clé que les APK de test envoyés jusqu'ici :
Android refuse de l'installer par-dessus. **Désinstalle d'abord l'ancienne version**
(les données de test seront effacées). Les futures versions de test s'appellent
« MédiPote (debug) » et s'installent à côté de la vraie application.

## Avant de publier sur le Play Store

1. **Politique de confidentialité** : obligatoire pour une app qui traite des données
   de santé. Un texte prêt à l'emploi est dans [PRIVACY.md](PRIVACY.md) ; il faut
   l'héberger à une adresse publique et renseigner cette adresse dans la console.
2. **Sécurité des données** (Data safety) : aucune donnée collectée ni partagée, tout
   reste sur l'appareil ; aucune donnée chiffrée en transit car rien n'est envoyé ;
   l'utilisateur peut tout effacer (Paramètres → Effacer toutes les données).
3. **Déclaration « Applications de santé »** : à remplir (suivi de médicaments, suivi
   du cycle, carnet de santé). MédiPote n'est pas un dispositif médical : ne pas
   présenter l'app comme établissant un diagnostic ou se substituant à un médecin.
4. **Alarmes exactes** : `USE_EXACT_ALARM` est réservée par Google Play aux réveils
   et calendriers. MédiPote a un agenda de rendez-vous et des rappels à heure fixe,
   mais la demande peut être refusée. Solution de repli : retirer la ligne
   `USE_EXACT_ALARM` du manifeste et retirer `android:maxSdkVersion="32"` de
   `SCHEDULE_EXACT_ALARM` ; l'utilisateur accorde alors lui-même la permission
   (raccourci déjà présent dans Paramètres → Rappels), sinon les rappels arrivent
   dans une fenêtre de 10 minutes.
5. **Public cible et classification du contenu** : adultes ; questionnaire IARC à remplir.
6. **Version cible** : l'app cible Android 16 (API 36), ce qu'exige actuellement le Play
   Store. Android 17 (API 37) sert à la compilation ; passer la cible à 37 après
   avoir testé sur un appareil Android 17.

## Fiche Play Store (brouillon)

**Titre** : MédiPote – rappels médicaments

**Description courte** (80 caractères max.) :
Tes traitements à l'heure, avec un petit compagnon à soigner. Simple et privé.

**Description** :

Ne plus oublier ses médicaments, en s'amusant.

MédiPote te rappelle chaque prise, et ton compagnon compte sur toi : donne-lui ses
médicaments à l'heure, il est en pleine forme ; oublie-les, il tombe malade.

• Guide intégré de 130 médicaments courants, posologie, stock et rachat
• Rappels à l'heure, relance en cas d'oubli
• Pilule contraceptive avec plaquette et semaines de pause
• Suivi du cycle en 4 phases, symptômes et journal de santé
• Rendez-vous médicaux avec rappels, carnet de santé (ordonnances, analyses)
• Hydratation, points, séries, boutique d'accessoires pour ton compagnon
• Widgets d'écran d'accueil, thème clair ou sombre, 6 couleurs

Tes données restent sur ton téléphone : pas de compte, pas de publicité, rien n'est
envoyé sur Internet.

MédiPote est un aide-mémoire et ne remplace pas l'avis d'un médecin ou d'un pharmacien.
