# LingoQuest

Une application Android (téléphone) pour apprendre l'anglais ou le français en **traduisant**, avec une
mécanique de jeu : XP, niveaux, vocabulaire à débloquer et séries à 3 vies.

## Le principe

- **Vocabulaire par fréquence et par niveau.** ~450 mots, triés du plus courant au moins courant et
  rangés par niveau CEFR : A1, A2, B1, B2, C1.
- **Une série = des traductions de plus en plus longues.** Chaque bonne réponse fait avancer la série :

  | Bonnes réponses d'affilée | Ce qu'il faut traduire |
  |---|---|
  | 0 – 4 | un **mot** (à taper) |
  | 5 – 9 | un **groupe nominal** simple : *the dog* |
  | 10 – 14 | groupe nominal + adjectif : *a big red house* |
  | 15 – 19 | groupe nominal complexe : *the woman with a book* |
  | 20 – 29 | **phrase simple** : *She is going to eat the apple* |
  | 30 et + | **phrase complexe** : négation, passé, futur, modaux, puis 2 puis 3 propositions (*and, but, because, when, if*), propositions relatives (C1) |

  Les mots se tapent ; les groupes nominaux et les phrases se construisent en touchant des **tuiles**
  (les mots de la réponse + des pièges : mauvais genre, mauvaise conjugaison, mots au hasard).
- **XP.** Chaque bonne réponse rapporte `XP de base du type de défi × (1 + 0,04 × position dans la série)`
  (mot 10, groupe nominal 15–30, phrase 40, phrase complexe 60), plus un bonus tous les 10 bons
  enchaînements. Plus on va loin, plus ça rapporte.
- **3 vies.** Au bout de 3 erreurs la série s'arrête et la suivante **repart de zéro** (mots simples).
  L'XP et le niveau gagnés sont conservés ; seuls la progression dans la série et le palier de difficulté
  retombent.
- **Niveaux.** 125 niveaux (`80 + 15 × (niveau − 1)` XP pour passer au suivant). **Tous les 25 niveaux** le
  niveau de langue monte : 1–25 A1, 26–50 A2, 51–75 B1, 76–100 B2, 101–125 C1.
- **Mots débloqués à chaque niveau.** Les niveaux de langue inférieurs sont acquis ; dans le niveau
  courant, chaque niveau du joueur débloque une nouvelle tranche de mots (par ordre de fréquence). Le
  premier niveau de chaque palier en donne au moins 12 (A1) ou 5, le dernier complète le palier.
  L'écran « Mes mots » liste tout, avec le niveau qui débloque chaque mot.
- **Explications après une erreur.** L'écran de correction montre ta réponse, la bonne réponse, puis « Comprendre l'erreur » : les faits sur les mots (genre, pluriel, table de conjugaison, auxiliaire) et les règles de grammaire de la phrase (articles, accord des adjectifs, place de l'adjectif, élision, passé composé avec être, négation, futur proche, verbe + infinitif, etc.), avec un exemple. Le générateur note les règles au moment où il construit la phrase, donc l'explication correspond à la phrase posée.
- Les mots ratés reviennent plus souvent, ainsi que les mots tout juste débloqués.
- **Deux sens** au choix : Français → English (par défaut) ou English → Français.

## Le générateur de phrases

`lingo/` est un module Kotlin/JVM sans dépendance Android, testé avec JUnit :

- `Lexicon` charge `lingo/src/main/resources/lexicon.txt` (un mot par ligne : noms avec genre et pluriel,
  adjectifs accordés et place en français, verbes conjugués avec auxiliaire, compléments, etc.).
- `Generator` construit **la phrase anglaise et la phrase française en même temps** à partir d'une
  petite grammaire, donc les deux côtés restent cohérents : déterminants et possessifs, accord des
  adjectifs (*bel / belle*, place avant/après le nom), pluriels, conjugaison, passé composé avec
  *avoir*/*être* et accord du participe, futur proche, négation (*ne … pas*), modaux (*can / must*),
  verbes + infinitif (*want to / décider de / commencer à*), élisions (*l'ami, j'ai, qu'il*) et
  contractions (*au, du*), *a / an*, relatives (*that / qui*), compléments (*with / in*).
- Seuls les mots **débloqués** sont utilisés. Si le vocabulaire est trop pauvre pour un type de défi
  (niveau 1, série longue), le générateur retombe sur un défi plus simple.
- `Progression` (XP, niveaux, déblocage), `GameSession` (une série, 3 vies, résultat de chaque réponse),
  `Judge` (correction : majuscules et ponctuation ignorées, article facultatif pour un mot, accents
  manquants acceptés avec un rappel).

### Ajouter des mots

Ajouter une ligne dans `lexicon.txt` (le format est décrit en tête du fichier). L'ordre dans le fichier
est l'ordre de déblocage dans un niveau CEFR. Les tests vérifient les doublons et la cohérence des formes.

## Limites connues

- La liste de mots est **écrite à la main** (≈450 mots) : l'ordre de fréquence est approximatif, ce n'est
  pas un corpus statistique. Pour une vraie liste de fréquence, il suffit de la convertir au format du lexique.
- Une traduction française → anglaise n'a qu'une réponse canonique (les tuiles ne permettent pas les
  synonymes) ; seuls les mots tapés acceptent plusieurs réponses (*big / large / tall*).
- *he/she* sont traduits par *il/elle*, *his/her* ne sont pas utilisés (*son/sa* serait ambigu).
- Le passé simple anglais est associé au passé composé français, l'imparfait n'est utilisé que pour *être*.

## Développement

```
./gradlew :lingo:test            # moteur de jeu (JVM, sans Android)
./gradlew :lingoquest:assembleDebug
```
