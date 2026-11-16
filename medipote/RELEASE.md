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
