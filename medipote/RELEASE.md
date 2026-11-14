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
