# Retour ciblé Windows — Downtify beta16

État initial : **READY_NOT_STARTED**. Aucune baseline ou observation de beta15 ne vaut résultat beta16.

- UTC / Europe/Paris, début et fin des essais :
- Modèle / Android, sans identifiant matériel :
- Version installée : 0.4.0-beta16 / code25, à vérifier.
- Hash APK vérifié avant installation :
- Installation `-r`, données conservées : NON TESTÉ
- Référence de mission et révision :
- Preuves privées hors Git et hors mémoire :

## Ordre des essais et arrêt sur incident

- Paquet Bluetooth ciblé, méthode d'identification :
- Dernière sortie connue AVANT essai, UTC / motif expurgé :
- Dernière sortie connue APRÈS petite sélection, UTC / motif expurgé :
- Nouvelle OOM / mort répétée / ANR / crash : NON TESTÉ
- Si incident nouveau : étape arrêtée, heure, conditions ; grande bibliothèque NON TESTÉE.
- Disponibilité d'`exit-info` sur cet Android et limites :

| Contrôle | PASS / FAIL / NON TESTÉ | Observations et limites |
| --- | --- | --- |
| Petite sélection : lecture, pause, Suivant, Précédent | NON TESTÉ | À faire avant la grande bibliothèque |
| Stabilité Bluetooth après petite sélection | NON TESTÉ | Historique ciblé comparé avant/après ; une ancienne OOM ne suffit pas |
| Publication legacy Android/Bluetooth vide | NON TESTÉ | `0` attendu ; n'est pas le compteur de la file interne |
| Première bibliothèque, compteur interne stabilisé | NON TESTÉ | Source = en-tête de la file dans l'app ; total indépendant ou inconnu |
| Même aléatoire relancé, même contexte | NON TESTÉ | Comparer les nombres internes ; système toujours0 attendu |
| Appui avant-dernier, remplissage achevé | NON TESTÉ | Index et suivant corrects ; préciser la preuve disponible |
| Pause pendant au moins un lot supplémentaire observé | NON TESTÉ | Sinon couverture non acquise |
| Nouvelle sélection pendant remplissage réel | NON TESTÉ | Ancienne sélection ne doit plus revenir |
| Stabilité Bluetooth après grande sélection | NON TESTÉ | Relecture `exit-info` limitée au paquet concerné |
| DJ préparé, Next fractionnaire en pause, shuffle OFF | NON TESTÉ | Titre, À suivre, position, même mix ; contrôle immédiat puis20s |
| DJ préparé, Previous après plus de3s dans le repère2, shuffle OFF | NON TESTÉ | Redémarre2sansréafficher1 ; deuxièmePrevious proche du début atteint1 |
| DJ préparé, Next fractionnaire en pause, shuffle ON | NON TESTÉ | Même protocole et limites |
| DJ préparé, Previous, shuffle ON | NON TESTÉ | Titre, À suivre et pause réellement observés |
| Stop DJ conserve les N classiques et leur ordre/permutation | NON TESTÉ | Compteurs internes avant N+mix / après N, occurrences vérifiées en privé |
| Relance puis Stop DJ conserve encore N classiques | NON TESTÉ | Ne doit pas devenir4puis5titres |
| Retour source présent / fallback position classique, si observable | NON TESTÉ | Ne pas confondre position classique sauvegardée et source d'une autre piste |
| Rendu seul sans file conservée, si disponible par UI normale | NON TESTÉ | Pas d'effacement pour fabriquer le cas |
| Android Auto réel | NON TESTÉ | Un téléphone ou un casque Bluetooth seul ne qualifie pas une unité voiture |

## Mesures

- File interne : valeurs horodatées et total de référence, ou total inconnu :
- File système legacy : valeurs horodatées, normalement0, mesurées séparément :
- PSS instantanées app / Bluetooth, unité et protocole :
- Cadence / maximum observé ; ne pas présenter comme pic instrumenté :
- Réaction UI / état prêt / premier son entendu : NON MESURÉS sauf protocole renseigné.
- Pour chaque FAIL : UTC, préconditions, étapes, attendu, observé, diagnostic expurgé, chemin local des preuves.
- Titres, captures, XML, empreintes d'occurrences et logs bruts NON IMPORTÉS dans mémoire/GitHub.

## Relais

- Mémoire MCP projet downtify, sujet beta16-usb-windows-results : NON SYNCHRONISÉE jusqu'au reçu vérifié.
- Hash/reçu final :
- Cas impossibles et raison exacte :
- Rapports beta14/beta15 préservés : à vérifier.

Ne pas convertir un PASS beta15 en PASS beta16 ni une baseline en test fonctionnel. Si les essais sont interrompus pour une nouvelle OOM, conserver tous les scénarios restants NON TESTÉS et ne pas les relancer automatiquement.
