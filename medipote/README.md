# MédiPote

Application Android (téléphone) qui aide à prendre ses traitements à l'heure. Un
petit compagnon virtuel compte sur toi : tu lui « donnes » tes médicaments à
chaque prise, il tombe malade si tu oublies, et tes points servent à
l'habiller dans la boutique.

| Accueil | Oubli | Boutique | Statistiques | Cycle |
|---|---|---|---|---|
| ![](docs/home.png) | ![](docs/home_sick.png) | ![](docs/shop.png) | ![](docs/stats.png) | ![](docs/cycle.png) |

## Fonctionnalités

- **Profil médical** : prénom, âge, poids, taille (IMC calculé), sexe,
  antécédents et allergies (suggestions + saisie libre), suivi du cycle.
- **Guide des médicaments intégré** : environ 130 médicaments courants en France,
  recherche sans accents par nom commercial, molécule (DCI) ou classe. Chaque
  fiche indique l'indication, la posologie habituelle de l'adulte, les
  précautions et si une ordonnance est nécessaire. Une alerte s'affiche si une
  allergie déclarée peut concerner le médicament. Saisie manuelle possible.
- **Posologie** : préréglages 1 à 4 fois par jour ou horaires libres,
  quantité (¼, ½, 1, 2…), forme, jours de la semaine (ex. traitement
  hebdomadaire), date de fin ou traitement au long cours, prise au repas.
- **Compagnon** : il réclame son médicament quand c'est l'heure, s'affaiblit
  (teint pâle, paupières lourdes, goutte de sueur) quand une prise est en retard,
  tombe malade (thermomètre) au-delà de 2 h et dort la nuit. On lui donne un
  médicament en faisant **glisser la pilule sur lui** ou avec le bouton
  « Donner ».
- **Notifications** à l'heure de chaque prise, puis relance 30 min plus tard si
  la prise n'est pas validée (alarmes exactes si autorisées, reprogrammées au
  redémarrage du téléphone).
- **Points**

  | Action | Points |
  |---|---|
  | Prise à l'heure (de 1 h avant à 30 min après) | +10 |
  | En retard (jusqu'à 2 h) | +6 (malus −4) |
  | Très en retard (au-delà de 2 h) | +3 (malus −7) |
  | Journée parfaite (toutes les prises à l'heure) | +20 |
  | Connexion quotidienne | +5 × jours de série, plafonné à +50 (dès le 10e jour) |
  | Paliers de série | 7 j : +30 · 30 j : +100 · 100 j : +300 · 365 j : +1000 |

- **Boutique** : 27 objets (couleurs, chapeaux, lunettes, accessoires de cou,
  décors), essayage avant achat, tous dessinés en Canvas.
- **Statistiques** : observance sur 7 et 30 jours, ponctualité, série,
  histogramme des 14 derniers jours (touchez une barre pour le détail),
  répartition sur 30 jours, observance par traitement, historique des points.
- **Cycle** (si « J'ai des règles » est activé dans le profil) : jour du cycle et
  phase, prochaines règles, fenêtre de fertilité et ovulation estimées,
  calendrier, durée moyenne calculée à partir des cycles enregistrés, bouton
  « Mes règles ont commencé ».
- **Thème clair / sombre / système** et couleurs du fond d'écran (Android 12+),
  dans les paramètres.

Les données (y compris de santé) restent sur le téléphone, dans un fichier
JSON privé exclu de la sauvegarde cloud.

## Architecture

| Dossier | Contenu |
|---|---|
| `data/` | Modèles sérialisables (`Models.kt`), stockage JSON atomique (`AppRepository`), guide des médicaments (`DrugGuide`) |
| `domain/` | Règles pures et testées : planning des prises (`Schedule`), points, série et santé du compagnon (`Game`), boutique, statistiques, cycle |
| `notifications/` | Alarmes (`ReminderScheduler`), récepteurs de rappel et de redémarrage |
| `ui/` | Jetpack Compose + Material 3 : un écran par dossier, `PetView` (compagnon dessiné en Canvas), navigation dans `MediPoteApp` |

Une prise prévue avant l'ajout du traitement n'est pas due : ajouter à 15 h un
médicament prévu à 8 h ne rend pas le compagnon malade.

## Compiler et tester

JDK 17+ et le SDK Android (plateforme 37).

```bash
./gradlew :medipote:assembleDebug       # medipote/build/outputs/apk/debug/medipote-debug.apk
./gradlew :medipote:testDebugUnitTest   # règles métier, écrans (captures dans medipote/build/screenshots) et parcours complet
adb install -r medipote/build/outputs/apk/debug/medipote-debug.apk
```

Minimum : Android 8.0 (API 26).

## Avertissement

MédiPote est un aide-mémoire : il ne remplace pas l'avis d'un médecin ou d'un
pharmacien. Les fiches du guide sont volontairement courtes et indicatives ; la
référence officielle est la
[base de données publique des médicaments](https://base-donnees-publique.medicaments.gouv.fr).
Le suivi du cycle repose sur la méthode du calendrier et n'est pas une méthode
de contraception.
