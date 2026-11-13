# Mission Codex Windows — téléphone USB, Downtify beta14

## Objectif et autorisation

Le propriétaire a branché son téléphone sur son PC Windows, activé le débogage USB et demandé explicitement de déléguer les essais à son Codex local. Le serveur opencode ne voit pas cet USB. Réaliser les essais sur **le PC Windows**, dans le projet Infra **local**, sans chercher un accès USB depuis le LXC.

Cette mission autorise la détection du téléphone, les captures locales ciblées de Downtify, l'installation en mise à jour de l'APK beta14 vérifiée, le lancement de l'application, les interactions nécessaires aux scénarios et la lecture des métriques/logs ciblés. Elle n'autorise ni effacement des données, désinstallation, réinitialisation du téléphone, export des comptes, sauvegarde des données applicatives, root, émulateur, configuration réseau/SSH, accès Proxmox, ni modification de serveurs. Ne pas demander à nouveau l'autorisation pour ces essais déjà demandés. Une boîte d'autorisation USB physique ou un écran de connexion à l'application peut nécessiter l'intervention du propriétaire ; aucun identifiant dans le chat.

Lire les AGENTS.md actifs et utiliser le MCP mémoire `agent_context`, projet canonique `downtify`, si disponible : ouverture du projet puis recherches ciblées en section `handoff`, sujet `dj-suite-genres-transitions-20261113`. Les instructions directes actuelles priment sur les souvenirs. Aucun build n'est nécessaire sur PC ou serveur pour tester cette APK.

## Livraison de référence

- Version : **0.4.0-beta14**, code **23**.
- Package : `com.henriquesebastiao.downtify.debug`.
- Activité : `com.henriquesebastiao.downtify.MainActivity`.
- APK : [partage vérifié](https://partage.bardreau.ovh/d/IOckPzl3pkVx0Q3/downtify-ai-0.4.0-beta14-20261113.apk), durée initiale 30 jours, 100 téléchargements à création.
- SHA-256 : `46c4abc2cacc19d8ad55e0d0f85b1f61fef028e4c87cb93a537b6ba50dc794bb`, 26 505 547 octets.
- Certificat SHA-256 : `6d83636875593dd3b5f2d9539105760349dcf109e1e22a0bf6aa03f8ac251214`, identique aux bêtas précédentes. Android contrôle la compatibilité à l'installation `-r`. En cas de signature incompatible, **arrêter**, ne pas désinstaller pour contourner l'erreur.
- Sources fonctionnelles `9e5d3ea`, notes de livraison `d18a005`, [PR Android #8](https://github.com/QuietForge7294/downtify-android/pull/8). La PR contient aussi le présent kit ; utiliser sa révision épinglée fournie avec le relais.
- Validation serveur de développement : 172 tests lecteur et 17 tests interface, style/Detekt/Lint/APK R8 réussis. **Cela ne vaut pas validation physique**.

## Fichiers à récupérer

Dans un dossier de mission local, conserver cette fiche et les trois fichiers suivants en gardant le script et le manifeste **dans le même dossier** :

- `tools/device-tests/Invoke-DowntifyBeta14.ps1`
- `tools/device-tests/beta14-artifact.json`
- `docs/testing/beta14-report-template.md`

Ne pas cloner tout le dépôt ni installer Android Studio si ADB suffit. Le script réutilise d'abord `adb.exe` existant dans PATH, le SDK Android habituel ou ses propres outils locaux. Avec `-BootstrapAdb`, il télécharge les Platform-Tools **officiels Google**, seulement si ADB est absent. Sources : [Platform-Tools](https://developer.android.com/tools/releases/platform-tools), [ADB](https://developer.android.com/tools/adb). Aucun émulateur et aucune ouverture réseau ADB.

## Préparation et vérification de syntaxe sur Windows

Dans PowerShell, avant d'exécuter le script téléchargé :

```powershell
$parseTokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PWD 'Invoke-DowntifyBeta14.ps1'),
    [ref]$parseTokens,
    [ref]$parseErrors
) | Out-Null
if ($parseErrors.Count -gt 0) { $parseErrors; throw 'Corriger la syntaxe avant execution' }
.\Invoke-DowntifyBeta14.ps1 -BootstrapAdb -InstallBeta -LaunchApp
```

L'exécution Windows réelle et ce parseur restent à vérifier par l'agent PC ; opencode n'a pas de runtime PowerShell. Ne pas changer globalement la stratégie d'exécution ou les pilotes Windows pour contourner une restriction. Si ADB est absent, le seul téléchargement prévu est celui des outils officiels ; si le téléphone est `unauthorized`, demander au propriétaire de le déverrouiller et d'accepter sa boîte USB. Pas de boucle de relance. En présence de plusieurs appareils, choisir explicitement `-DeviceSerial` après identification avec le propriétaire.

Le script refuse un APK dont le hash ou la taille diffère, ainsi qu'une version installée supérieure à 23. Il installe avec `adb install -r`, sans `-d`. Il ne supprime ni données ni cache. Les champs `expected_artifact_sha256` et `launch_requested` décrivent une référence et une demande, pas une preuve du binaire installé ni du lancement réel. Le résultat `baseline.json` indique explicitement **functional_tests=NOT_RUN** : il prépare le test, sans le simuler.

Les preuves restent dans `%LOCALAPPDATA%\DowntifyTests\beta14\<UTC>`, hors du dépôt. Aucun titre, nom de compte, URL de session, numéro de série, capture ou log brut dans GitHub ou dans le rapport partagé. Le journal de commandes remplace l'identifiant de téléphone par `<DEVICE>`.

## Essais prioritaires

Noter modèle/Android, version APK, UTC et Europe/Paris, protocole exact et résultat observé. Respecter les commandes/traçabilité des AGENTS actifs ; masquer les secrets. Utiliser la vraie UI et ses libellés. Ne pas choisir des coordonnées de clic sans voir l'écran ou sa hiérarchie actuelle. Le propriétaire a autorisé les essais : progresser sans demander une confirmation à chaque clic.

| ID | Essai | Preuve attendue et distinction nécessaire |
| --- | --- | --- |
| Q1 | Playlist de plus de 130 titres, lancement aléatoire | Nombre réel de titres, nombre dans la file après remplissage, absence de troncature à 10/33. Ne pas publier les titres. |
| Q2 | Dépasser 33 titres dans ce parcours | Atteindre au moins 40 transitions et vérifier les occurrences. Distinguer clics Suivant, fins naturelles et fins accélérées en plaçant la lecture près de la fin. Des clics Suivant ne prouvent pas 40 fins naturelles. Ne pas attendre plusieurs heures si un protocole accéléré valide le même événement de fin ; noter le protocole et les limites. |
| Q3 | Bibliothèque entière, bouton aléatoire puis appui direct sur un titre proche de la fin | Délai mesuré de réaction et de premier son séparés, file complète après remplissage, titre suivant correct, pas de crash/ANR. Ne pas attribuer automatiquement un délai réseau au thread UI. |
| Q4 | Pause et nouvelle sélection pendant remplissage | Les lots suivants ne reprennent pas la lecture et n'écrasent pas la nouvelle sélection. |
| Q5 | Suivant/Précédent en DJ préparé, avec file classique conservée puis mix seul | Navigation dans les repères du mix, pas de retour implicite à la file classique ; pause préservée. Refaire sous aléatoire. |
| Q6 | Mix DJ direct prêt placé en prochain dans une file aléatoire | Il devient le prochain élément joué. Une commande refusée doit rester visible et ne pas sortir du mix. |
| Q7 | Minuterie 5 minutes | Compte à rebours visible et décroissant après au moins 10 secondes, annulation, absence de reprise intempestive. L'expiration physique n'est validée qu'après attente réelle. |
| Q8 | Paramètres → Lecture | Choix Désactivé/Transitions/DJ et durée de fondu 1–12 s accessibles ; changements visibles et cohérents. Un fondu local n'est pas une preuve de mixage rythmique. |
| AA | Android Auto, si une vraie voiture/unité compatible est disponible | Tester Q2/Q5 depuis cette surface. Sans unité connectée : **NON TESTÉ**, ne pas confondre écran du téléphone, simulation MediaController et essai voiture. |

Séparer les besoins de manipulation humaine (câble, USB, conduite, écoute) de ce que Codex peut mesurer. Arrêter un scénario qui produit un crash/ANR reproductible et collecter les preuves ciblées avant toute nouvelle tentative.

## Captures et ressources

La capture est volontairement laissée au Codex PC après vérification du téléphone déverrouillé et de la fenêtre Downtify réellement focalisée. Une activité reprise dans `dumpsys activity` seule ne suffit pas. Inspecter aussi `dumpsys window` et l'état keyguard local ; si l'état reste ambigu, demander au propriétaire de revenir à Downtify plutôt que capturer autre chose.

Avec le chemin ADB et le numéro de série déjà identifiés localement, et uniquement une fois ces conditions vérifiées :

```powershell
# $adbExe et $deviceSerial proviennent de la détection locale, sans les publier.
# $proofFolder est un dossier privé de preuves ; choisir un nom temporaire unique.
$frameName = 'downtify-beta14-' + [Guid]::NewGuid().ToString('N') + '.png'
$remoteFrame = '/sdcard/Download/' + $frameName
& $adbExe -s $deviceSerial shell screencap -p $remoteFrame
if ($LASTEXITCODE -ne 0) { throw 'Capture echouee' }
& $adbExe -s $deviceSerial pull $remoteFrame (Join-Path $proofFolder $frameName)
if ($LASTEXITCODE -ne 0) { throw 'Recuperation echouee; conserver le diagnostic' }
& $adbExe -s $deviceSerial shell rm $remoteFrame
```

Ces commandes suppriment seulement l'image temporaire créée pour ce test. Ne pas employer `adb exec-out ... > image.png` dans Windows PowerShell 5.1 : la redirection de texte peut corrompre le binaire. Le script de baseline, lui, n'effectue aucune capture.

Pour les interactions UI, l'agent PC peut lire une hiérarchie `uiautomator dump`, puis la récupérer avec `adb pull` et la garder locale. Vérifier d'abord la fenêtre Downtify ; ne pas capturer d'autres applications ou un écran verrouillé. Les extractions UI peuvent contenir des titres et restent privées.

Mesurer `adb shell dumpsys meminfo com.henriquesebastiao.downtify.debug` avant/après les scénarios de grosse file. PSS Android n'est pas heap Java ni consommation batterie. En cas de bug, isoler les logs du PID Downtify et l'heure de reproduction ; conserver le brut localement, rapporter uniquement la pile/erreur expurgée utile. Aucun logcat global, bugreport complet, copie de base de données ou fichier de session n'est nécessaire.

## DJ froid et hebdomadaires

Les sets hebdomadaires ont déjà été activés explicitement sur le compte du propriétaire. Ne pas réécrire les préférences. Observer les cartes visibles et leurs états réels ; ne pas déclencher plusieurs gros rendus pour tester l'écran.

Un fichier préparé attend l'analyse de tous ses titres. Le direct commence avec les deux premiers puis prépare la suite. L'interface beta14 privilégie le direct progressif, mais ne démontre pas une baisse du coût des analyses à froid. Anciennes mesures : 530,485 s pour un préparé de 12 titres ; 5,089 s pour un manifeste HLS avec segments sur les mêmes 12 titres déjà analysés. Ce sont des parcours différents, et le manifeste n'est pas le premier son sur téléphone. Ne pas les présenter comme un gain comparable ni une mesure P95.

Sur le téléphone, noter séparément clic → réaction UI, clic → état prêt et clic → premier son effectivement entendu, mode choisi, cache chaud/froid **si connu**. Une absence d'information sur le cache doit rester « inconnu ».

## Retour et coordination

Un seul agent pilote ce téléphone pendant les scénarios. Opencode garde la propriété des corrections de source Android : ne pas modifier une autre branche en parallèle, ne pas compiler sur le serveur depuis ce relais. Pour un bug, fournir ID du test, version exacte, étapes minimales, résultat attendu/observé, horodatage, métriques, pile expurgée et chemin privé des preuves. Ne pas masquer le problème par une désinstallation ou une baisse de couverture.

Copier le modèle de rapport, remplir PASS/FAIL/NON TESTÉ et les limites. Enregistrer un passage de relais concis dans `agent_context`, projet `downtify`, section `handoff`, sujet **beta14-usb-windows-results** avec références à ce rapport et à la PR #8. Ne pas remplacer le passage de relais d'opencode ; des agents différents peuvent y travailler. Si le MCP est absent, conserver le rapport local et donner le texte utile au propriétaire, sans prétendre l'avoir synchronisé. Ne pas publier de commentaire GitHub automatiquement ; le suivi public sera mis à jour avec le bilan expurgé.
