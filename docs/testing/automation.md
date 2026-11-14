# Livraison et tests USB partagés entre agents

Ces outils remplacent les séquences répétées de commandes et de clics par des plans,
des points de reprise et des résumés courts. Ils ne remplacent pas les restrictions du
propriétaire, le garde-fou de build ou les mesures physiques. Python 3.11+ sans dépendance
externe ; OCR Windows facultatif. Les manifests bêta14/15/16 et leurs rapports restent intacts.

## Ordre de travail

1. Regrouper les corrections Android/serveur/UI constituant un candidat cohérent.
2. Utiliser les tests ciblés pendant le développement. Préparer les classes Kotlin dans
   une invocation distincte des tests. Conserver caches et sorties ; aucun `clean`.
3. Exécuter les contrôles de livraison une fois sur les sources finales. Une modification
   des sources invalide le plan. Le pilote reprend les étapes déjà réussies de ce plan.
4. Certifier puis partager l'APK et son manifeste ; transférer une seule fois les fichiers nécessaires.
5. Installer une fois avec conservation des données, puis lancer la campagne USB groupée.
   Une nouvelle installation n'est pas nécessaire pour reprendre un cas déjà terminé.
6. Publier le bilan expurgé et les références de source dans `agent_context`, projet `downtify`.
   Relire/vérifier le reçu. Garder les journaux, XML, images et titres hors GitHub et mémoire.

Un seul build Gradle lourd à la fois, toujours sur opencode et sous le garde-fou installé.
Le verrou système reste commun aux worktrees. Un seul pilote par téléphone, grâce à un
verrou OS commun aux répertoires de campagne. Les lectures serveur, revues de code, OCR
d'images existantes et tests Python légers indépendants peuvent se faire en parallèle.
Ne pas lancer plusieurs builds, émulateurs ou pilotes USB pour gagner du temps.

## Pilote de livraison

Depuis le checkout Linux opencode, choisir un nouveau dossier privé **hors checkout** :

```sh
python3 tools/automation/release_pipeline.py plan --out /root/downtify-runs/CANDIDAT --label CANDIDAT
python3 tools/automation/release_pipeline.py run --out /root/downtify-runs/CANDIDAT --until player-tests
```

Le plan imprime les tâches. Le pilote appelle exclusivement
`/usr/local/bin/downtify-android-build --wrapper /chemin/absolu/gradlew -- --offline TACHE --console=plain`.
Étapes distinctes, séquentielles : `spotlessCheck`, `detekt`,
`:core:player:compileDebugUnitTestKotlin`, `:core:player:testDebugUnitTest`,
`:app:compileBetaKotlin`, `:app:assembleBeta`, `:app:lintBeta`.

Pour poursuivre le même plan après la préparation du candidat final :

```sh
python3 tools/automation/release_pipeline.py run --out /root/downtify-runs/CANDIDAT
```

Le contenu de tous les fichiers suivis et non ignorés, y compris les sources non committées,
ainsi que le commit HEAD sont identifiés. Un changement bloque la reprise et nécessite un
nouveau plan. Un échec, code 75 ou interruption laisse un reçu bloquant, sans nouvelle
tentative automatique. Examiner le journal et les reçus du garde-fou avant un nouveau plan.
Ne pas contourner un verrou/refus mémoire ni réinitialiser automatiquement une unité échouée.
Après un timeout ou une interruption du pilote, inspecter l'unité existante : son arrêt
n'est pas prouvé par celui du contrôleur. Les limites de `docs/android-build-budget.md`
restent obligatoires. Chaque commande SSH et chaque commande distante doit être annoncée
intégralement dans le chat avant son exécution, conformément aux consignes du propriétaire.

Après toutes les étapes, utiliser les outils Android SDK existants pour vérifier l'APK :

```sh
python3 tools/automation/release_pipeline.py certify --out /root/downtify-runs/CANDIDAT \
  --apk /chemin/app-beta.apk --aapt /chemin/sdk/build-tools/VERSION/aapt \
  --apksigner /chemin/sdk/build-tools/VERSION/apksigner --certificate EMPREINTE_SHA256_APPROUVEE
```

La certification exige tous les reçus, vérifie que l'APK correspond à celui de l'assemblage
protégé, lit son package/version et vérifie sa signature. Elle produit `artifact.json` :
SHA-256, taille, certificat, source, plan et état des contrôles automatisés. Les tests
physiques restent `NOT_RUN`. Le script ne charge ni ne copie une clé et ne publie rien
automatiquement. Utiliser la procédure de partage existante après validation, sans secours
par une autre signature. Une URL de téléchargement n'est pas une preuve d'intégrité.

Sorties : un JSON par étape, reçus privés du garde-fou, logs privés, audit JSONL UTC et
Europe/Paris, durées et manifeste. `plan` est utilisable sans Gradle ; `run` exige Linux
avec le garde-fou installé. L'horaire Paris suit les règles UE de 1996 à la date présente ;
adapter le code si la réglementation change, UTC restant la référence.

## Pilote téléphone

Windows : utiliser le `adb.exe` Google déjà installé, sans téléchargement répété d'outils.
Dans PowerShell, préparer les arguments communs (les chemins sont des exemples) :

```powershell
$deviceArgs = @('--adb', "$env:LOCALAPPDATA/DowntifyTests/tools/platform-tools/adb.exe",
  '--out', "$env:LOCALAPPDATA/DowntifyTests/CANDIDAT/campagne",
  '--manifest', 'C:/livraison/artifact.json', '--apk', 'C:/livraison/app-beta.apk')
python tools/automation/phone_suite.py preflight @deviceArgs
python tools/automation/phone_suite.py install @deviceArgs
python tools/automation/phone_suite.py run @deviceArgs --plan C:/livraison/campagne.json
```

`preflight` lit la version et les historiques de sorties, sans lancer l'app ou la musique.
`install` vérifie SHA-256 et taille, refuse un downgrade et utilise seulement `adb install -r`.
Android impose la compatibilité de signature de la mise à jour. Aucun effacement, désinstallation,
force-stop ou lancement automatique n'est implémenté. Le manifeste doit venir d'une source
de confiance, avec empreinte revue ; les nouveaux candidats doivent utiliser la certification
ci-dessus. Un reçu d'installation empêche une seconde tentative dans le même dossier.

Le pilote exige un appareil USB physique autorisé ; en cas de plusieurs appareils, préciser
`--serial` sans publier cette valeur. Aucun scan réseau ni émulateur. Garder le téléphone
déverrouillé et Downtify au premier plan pour les entrées UI. Les taps sont calculés à partir
d'un sélecteur exact et unique de l'arbre actuel, jamais depuis une ancienne capture.

Chaque cas du plan possède un `id`, des `steps` et au moins une assertion observable.
Opérations :

| Opération | Paramètres | Résultat |
| --- | --- | --- |
| `tap` | `selector`: `text`, `description` et/ou `resource_id` exacts | Un seul nœud activé ; sinon arrêt `NEEDS_VISUAL` |
| `back` | aucun | Retour si Downtify est au premier plan |
| `wait` | `seconds`, de 0 à 30 | Surveillance santé toutes les deux secondes |
| `capture` | aucun | PNG binaire direct `exec-out screencap -p`, hors dépôt |
| `queue_count` | `expect.min` / `expect.max` | Compteur interne français `N éléments` de l'écran courant |
| `media` | `expect.state` / `expect.legacy_queue` | État de la session native ciblée, sans titres |

Voir `tools/automation/usb-smoke.example.json`. Adapter les limites au scénario réel ;
ce plan d'exemple suppose que le propriétaire a déjà ouvert une grande file en pause.
Il ne constitue pas la campagne complète DJ/latence/réseau/Android Auto.
La file legacy Bluetooth peut volontairement être vide ; elle ne mesure pas la file interne.

Les historiques de **Downtify et Bluetooth** sont comparés avant/après chaque étape et
pendant les attentes. Toute nouvelle sortie arrête la campagne (y compris OOM/ANR/crash).
Les anciens crashes ne sont pas déclarés nouveaux. Seule l'installation tolère les nouvelles
sorties `PACKAGE UPDATED`. Une réponse de diagnostic inconnue bloque la qualification.
La baseline et les checkpoints sont persistés : reprendre un plan terminé ne rejoue pas les
actions et ne masque pas les sorties apparues pendant une interruption. Un cas interrompu
ou échoué exige l'examen des preuves et un nouveau dossier de campagne après correction.

`automated_checks_passed` signifie seulement que les assertions du cas ont réussi.
Les `manual_checks` (écoute, vraie unité Android Auto, appréciation de transition) restent
`NOT_RUN`. Rapporter séparément leurs observations réelles et les mesures de latence.
Ne jamais transformer une capture, un manifeste disponible ou un test synthétique en preuve sonore.

## Compteur sans attendre l'inactivité UI

Pendant un chargement animé, `uiautomator dump` peut ne pas produire d'arbre. Capturer des
images à une fréquence modérée dans le scénario, puis utiliser l'OCR hors ligne en un lot.
Ne pas répéter des dumps XML sans fin. Créer un JSON privé contenant un tableau de chemins
absolus des images, puis exécuter **Windows PowerShell 5.1** :

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File tools/automation/read_queue_counter.ps1 -InputJson C:/preuves/images.json
```

L'autorisation d'exécution ci-dessus est limitée au processus, sans modification de politique
système. Le script utilise l'OCR Windows local, recadre les 18 % supérieurs et renvoie uniquement
le numéro d'image et un compteur. Aucun titre ni texte OCR brut ne sort. `NEEDS_VISUAL` ou
`OCR_ERROR` ne sont jamais remplacés par une valeur supposée. La langue OCR Windows doit
être disponible. Cette lecture sert à exploiter les captures ; elle ne mesure pas à elle seule
les temps d'annulation ou de premier son. Les limites du cadrage doivent être revues si l'UI change.

`mapping_frames.py MAPPING STACK` inspecte les candidats d'un mapping R8 sans publier les
messages d'exception. Ce n'est pas un Retrace officiel complet ; conserver tous les candidats
d'inlining utiles et confirmer dans les sources avant de conclure.

## Vérification et limites connues

```sh
python -m unittest discover -s tools/automation/tests -v
```

Ces tests vérifient les erreurs, reprises, verrous, compteurs et preuves de livraison avec
des données synthétiques ; ils ne lancent ni build Gradle ni ADB.
Le modèle `tools/automation/github-workflow.example.yml` prévoit Windows et Linux,
indépendamment des builds Android, sans doublon push/PR. Il n'est pas actif : le jeton OAuth
disponible le 14 novembre refuse les changements de workflow (scope `workflow` absent).
Un mainteneur disposant de cette permission pourra le copier vers
`.github/workflows/automation-tools.yml`. Ne pas annoncer de résultat CI sans exécution réelle.

Le 14 novembre 2026 : 22 tests réussis sous Windows en 0,76 s, incluant code 75,
APK lié à l'assemblage et validation JUnit. Consulter aussi le résultat CI courant.
OCR local : deux captures existantes lues à 27 970 éléments, deux `NEEDS_VISUAL`, lot < 1 s.
Précontrôle USB : aucun appareil autorisé trouvé lors de cette vérification ; aucun scénario
physique de ce nouvel outil n'est déclaré réussi. Le pilote de build doit encore être utilisé
sur un candidat final opencode pour obtenir ses premiers reçus opérationnels.

Gain établi par construction et tests : les étapes réussies et cas terminés ne sont pas rejoués,
aucune boucle de relance, une capture PNG ne nécessite plus de fichier distant/pull/rm,
le lot OCR est traité dans un seul processus, les sorties publiques sont des résumés courts.
Les durées comparatives complètes d'une livraison et d'une campagne restent à mesurer.
Ne pas présenter ces mécanismes comme un gain P95 Android déjà mesuré.
