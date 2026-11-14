# Recontrôle Codex Windows — Downtify beta16

Le propriétaire a autorisé les essais sur son téléphone USB depuis son Codex Windows. Reprendre la tâche locale existante, lire les AGENTS actifs et le bilan expurgé beta15 dans le MCP mémoire du projet `downtify`. **Préserver les rapports et preuves beta14/beta15.** Un seul agent pilote le téléphone ; opencode garde la propriété des sources. État initial de cette mission : **READY_NOT_STARTED** ; aucun scénario physique exécuté ou préattribué.

## Identité et préparation

- Version `0.4.0-beta16`, code **25**, package `com.henriquesebastiao.downtify.debug`.
- APK `downtify-ai-0.4.0-beta16-20261114.apk`, **26522591 octets** ; SHA-256 `ef4d215391168c92851ffdfd69466c963dd67a63318156c17774e1cff3304a99`.
- Même certificat `6d83636875593dd3b5f2d9539105760349dcf109e1e22a0bf6aa03f8ac251214` ; mise à jour `adb install -r`, sans désinstallation, effacement, downgrade ni sauvegarde des données privées.
- Source fonctionnelle `551082d82c899d2e26d207f44e404cb831e03182`, [PR #10](https://github.com/QuietForge7294/downtify-android/pull/10). Les 187 tests lecteur, Android Lint et l’assemblage optimisé passent selon le relais opencode. Les tests automatisés ne valident pas le téléphone.
- [APK beta16 publiée et téléchargement vérifié](https://partage.bardreau.ovh/d/4em660AR9mPKEM6/downtify-ai-0.4.0-beta16-20261114.apk). Les URL canoniques sont épinglées dans `tools/device-tests/beta16-artifact.json`. Ne pas remplacer l'APK par une autre bêta ni modifier l'empreinte pour obtenir un succès. Le script refuse une publication incomplète.

Récupérer côte à côte `tools/device-tests/Invoke-DowntifyBeta16.ps1` et `tools/device-tests/beta16-artifact.json` de la révision épinglée du relais, ainsi que `docs/testing/beta16-report-template.md`. Ne pas réutiliser l'installateur beta15. Réutiliser l'ADB officiel déjà identifié sur le PC ; ni émulateur, ni compilation, ni nouveau rendu DJ, ni SSH/PVE, ni changement de préférences serveur ne sont nécessaires.

```powershell
$parseTokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PWD 'Invoke-DowntifyBeta16.ps1'), [ref]$parseTokens, [ref]$parseErrors
) | Out-Null
if ($parseErrors.Count -gt 0) { $parseErrors; throw 'Corriger la syntaxe avant execution' }
.\Invoke-DowntifyBeta16.ps1 -InstallBeta
```

Le script crée une baseline locale séparée sous `%LOCALAPPDATA%\DowntifyTests\beta16`. Cette baseline ne marque aucun scénario PASS. Vérifier visuellement la version après installation ; `launch_requested` ne prouve pas qu'une fenêtre s'est ouverte.

## D'abord Bluetooth et une petite sélection

1. Avant lancement, relever UTC/Europe/Paris, le dernier état de sortie du **seul paquet Bluetooth concerné** et une mesure mémoire instantanée. Ne pas changer l'état Bluetooth, l'appairage ou les appareils connectés pour fabriquer un résultat.
2. Si le paquet du téléphone n'est pas encore connu, la commande ciblée `adb -s <DEVICE> shell pm list packages bluetooth` permet de l'identifier. Pour le paquet déjà observé `com.android.bluetooth`, utiliser `adb -s <DEVICE> shell dumpsys activity exit-info com.android.bluetooth`. Adapter uniquement au paquet Bluetooth réellement identifié. Sur un Android ne fournissant pas `exit-info`, noter cette limite ; elle ne vaut pas absence d'incident.
3. Conserver la sortie brute **localement**, sans l'imprimer ou l'importer en mémoire/GitHub. Comparer uniquement les entrées horodatées avant/après l'essai : une ancienne sortie OOM beta15 n'est pas une nouvelle OOM beta16. Ne pas exécuter `exit-info` sans paquet ni capturer un logcat général, un bugreport complet ou l'inventaire de toutes les applications.
4. Ouvrir Downtify et une **petite sélection de quelques titres** avant la bibliothèque entière. Vérifier lecture, pause, Suivant, Précédent et stabilité. Relire le seul `exit-info` Bluetooth ciblé et la mémoire après ce petit essai.
5. **À la première nouvelle OOM Bluetooth, mort répétée du processus, ANR ou crash : arrêter les scénarios et ne pas lancer la grande bibliothèque.** Garder l'heure et le diagnostic ciblé expurgé ; aucune boucle de réouverture, réinstallation ou modification des limites.

## File système et file propre : mesures distinctes

La file legacy exposée par la session Android/Bluetooth doit être **vide par conception**, y compris pendant une grande sélection. Un compteur système `0` est ici le résultat attendu ; il ne signifie pas que la bibliothèque ou la file interne est vide. Ne pas tenter de rendre cette publication complète pour récupérer un compteur.

Le **compteur de l'en-tête File d'attente dans l'application** mesure la file propre. Pour ce recontrôle, relever ce nombre interne par l'UI et l'horodater ; ne pas lui substituer le nombre de la session système. Un extrait ciblé et privé de la session Downtify peut vérifier la publication legacy vide, mais ne pas conserver les sessions des autres applications ni les titres privés dans le rapport partagé.

## Recontrôle bibliothèque, seulement après le petit essai stable

1. Depuis Titres, lancer l'aléatoire, observer le remplissage et relever le compteur interne stabilisé. Le nombre système doit rester `0` ; l'app doit rester réactive.
2. Relancer dans le **même contexte**. Le compteur interne doit retrouver le total précédent si la bibliothèque n'a pas changé et dépasser500 lorsque plus de500titres sont réellement disponibles.
3. Sélectionner l'avant-dernier titre de la liste complète. Une file500/index498 transitoire est possible au premier lot, mais ne doit pas rester le résultat final d'une sélection plus grande. Vérifier le dernier titre avec Suivant.
4. Tester pause et changement de sélection pendant au moins un lot supplémentaire réellement observé. Si le remplissage est déjà terminé, noter que l'annulation en cours de chargement n'a pas été couverte.
5. Relever de nouveau le seul historique de sortie Bluetooth concerné. Arrêter à tout nouvel incident, même si l'UI fonctionne encore.

Après stabilisation, relever le compteur interne et laisser la lecture en pause assez longtemps pour sauvegarder le point de reprise. Si les contrôles Bluetooth restent stables, fermer uniquement Downtify avec `adb -s <DEVICE> shell am force-stop com.henriquesebastiao.downtify.debug`, puis relancer avec `adb -s <DEVICE> shell am start -n com.henriquesebastiao.downtify.debug/com.henriquesebastiao.downtify.MainActivity`. Vérifier le compteur interne restauré, la pause et la file système vide dès ce démarrage, puis comparer à nouveau les dates de sortie Bluetooth. Aucun effacement de données.

Ne pas supposer le total du catalogue à partir d'un ancien relevé. Si aucune référence indépendante n'est disponible, conclure seulement sur les nombres observés, leur stabilité et les répétitions. Des clics Suivant ne constituent pas des fins naturelles de titres.

## DJ préparé : repères fractionnaires, pause et sortie du mode

Réutiliser un set préparé déjà prêt. Identifier visuellement le plein écran, le mix et les vrais boutons avant chaque action ; les coordonnées du mini-lecteur ne suffisent pas.

1. En pause, noter le titre courant, À suivre, la position et le compteur interne de la file classique conservée. Les titres et captures restent privés ; le rapport partagé utilise des désignations neutres.
2. Avec shuffle **OFF**, appuyer sur Suivant. Le mix doit rester sélectionné, le titre courant devenir le repère suivant et À suivre être actualisé. Vérifier immédiatement puis après20s en pause. La position ne doit pas rester juste avant la fin fractionnaire du précédent (ancien cas153051ms pour une fin réelle153.0517s).
3. Depuis plus de3s à l'intérieur du deuxième repère, Précédent doit redémarrer ce deuxième repère **sans réafficher le premier**. Un second Précédent près de ce redémarrage doit atteindre le premier repère. Contrôler titre, À suivre, position et pause après chaque action.
4. Refaire avec shuffle **ON**. Relever le protocole exact si le set disponible ne permet pas d'atteindre une frontière fractionnaire ; ne pas fabriquer cette couverture à partir d'une seule position entière ou d'un seek scripté hors UI.
5. En pause, **désactiver volontairement le mode DJ**. Si la file contient N classiques plus un mix, la sortie doit conserver les N occurrences classiques, leur ordre/permutation et la pause, en retirant les seules entrées DJ. Elle ne doit pas les remplacer par les4titres du rendu. Noter le compteur interne avant/après et contrôler les occurrences en privé.
6. Relancer le même mix puis l'arrêter à nouveau : retrouver les mêmes N classiques, et non une file passée de4à5. Si la piste audible du mix existe dans la file classique, sa reprise peut suivre son point source ; sinon le retour doit utiliser la position classique sauvegardée, pas la position source d'un autre morceau du mix.

Une reprise en titres ordinaires du rendu n'est attendue **que si aucune file classique n'a réellement été conservée**. Ne pas effacer volontairement la bibliothèque ou les données pour fabriquer ce cas. Android Auto réel reste NON TESTÉ sans unité voiture ; le transport notification/Bluetooth seul ne le remplace pas.

## Retour attendu

Remplir `beta16-report-template.md` uniquement avec les observations effectives. Captures, XML, titres, identifiants, empreintes d'occurrences et logs bruts restent dans le dossier privé PC, hors Git et mémoire. Les PSS sont des instantanés ; le maximum observé n'est pas un pic instrumenté. Réaction UI, état prêt et premier son entendu sont des délais distincts.

Publier un bilan final expurgé dans `agent_context`, projet `downtify`, section `handoff`, sujet **beta16-usb-windows-results**, sans remplacer les notes beta14/beta15 ni le relais principal opencode. Inclure limites, NON TESTÉS et incidents horodatés. Exiger un reçu vérifié ; sinon garder le rapport local NON SYNCHRONISÉ. Aucun PASS préattribué, aucun commentaire GitHub automatique demandé, aucune relance de set hebdomadaire ni changement d'infrastructure.
