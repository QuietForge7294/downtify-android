# Recontrôle Codex Windows — Downtify beta15

Le propriétaire a autorisé les essais sur son téléphone USB depuis son Codex Windows. Reprendre la tâche locale existante, lire les AGENTS actifs et le rapport final beta14 enregistré dans le MCP `downtify/handoff/beta14-usb-windows-results`. Ne pas remplacer ce rapport par les nouveaux résultats et ne pas importer ses preuves privées dans la mémoire.

## APK et installation

- APK [0.4.0-beta15/code24](https://partage.bardreau.ovh/d/161J09Gjcz9reUn/downtify-ai-0.4.0-beta15-20261113.apk) ; SHA-256 `142d71d5c80fa46a74f1653d01988bc126f4dac18004d07061e63afe0f42b214` ; 26505547 octets.
- Même package `com.henriquesebastiao.downtify.debug` et certificat `6d83636875593dd3b5f2d9539105760349dcf109e1e22a0bf6aa03f8ac251214` ; installation en mise à jour avec `-r`, sans désinstallation, effacement ou downgrade.
- Source fonctionnelle `f592890`, [PR #9](https://github.com/QuietForge7294/downtify-android/pull/9). Aucun nouveau serveur, rendu DJ, build, accès SSH/PVE ou changement de préférences requis.
- Les 178 tests lecteur passent ; les deux défauts ont d'abord été reproduits en tests échoués. La vérification physique de beta15 reste à faire.

Récupérer `tools/device-tests/Invoke-DowntifyBeta15.ps1` et `tools/device-tests/beta15-artifact.json` de la révision du présent relais, côte à côte dans un dossier PC. Récupérer aussi `docs/testing/beta15-report-template.md`.
**Ne pas réutiliser l'installateur beta14 pour installer beta15** : chaque script est épinglé à sa version et à son empreinte.

```powershell
$parseTokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
    (Join-Path $PWD 'Invoke-DowntifyBeta15.ps1'), [ref]$parseTokens, [ref]$parseErrors
) | Out-Null
if ($parseErrors.Count -gt 0) { $parseErrors; throw 'Corriger la syntaxe avant execution' }
.\Invoke-DowntifyBeta15.ps1 -InstallBeta -LaunchApp
```

ADB officiel est déjà installé sur le PC selon le rapport beta14. Le script réutilise les emplacements connus ; `-AdbPath` permet de réutiliser un autre chemin déjà identifié. Ne pas réinstaller les outils sans nécessité. La baseline n'est pas un test fonctionnel, et `launch_requested` ne prouve pas que l'app est affichée. Vérifier visuellement la version, la fenêtre et le bouton ciblé.

## Deux corrections à confirmer en priorité

### Bibliothèque chargée plusieurs fois

1. Depuis Titres, lancer l'aléatoire sur toute la bibliothèque et attendre la stabilisation. Relever les nombres avec horodatage.
2. Relancer l'aléatoire dans le **même contexte**, sans changer les filtres. Le remplissage doit dépasser 500 et retrouver le nombre complet observé précédemment si la bibliothèque n'a pas changé.
3. Revenir à Titres, sélectionner l'avant-dernier morceau de la liste complète. Après remplissage, l'index doit correspondre à l'avant-dernier du total, et Suivant au dernier. Un index498/file500 transitoire est normal pour le premier lot ; il ne doit pas devenir la file finale si plus de500titres sont attendus.
4. Tester pause puis nouvelle sélection pendant un remplissage **où au moins un lot supplémentaire est observé**. Sinon, noter la limite de couverture.

Obtenir un total indépendant si l'UI ou une source déjà autorisée le permet. Si ce total reste inconnu, prouver seulement que les chargements successifs retrouvent le même nombre et dépassent500 ; ne pas déclarer la complétude de tout le catalogue sans référence. Ne pas utiliser un ancien relevé partiel comme total. La correction a été reproduite sur1501titres synthétiques puis vérifiée sur ce scénario et le catalogue synthétique28001 ; ce ne sont pas les nombres du téléphone.

### DJ préparé Suivant/Précédent

1. Réutiliser le set préparé déjà prêt lors de beta14 ; ne pas lancer un nouveau rendu pour cet essai.
2. Attendre l'ouverture réelle du plein écran, vérifier DJ actif, le mix courant et le repère Àsuivre. La disposition du mini-lecteur ne suffit pas.
3. En pause, avec shuffle actif, appuyer une fois sur le vrai bouton Suivant. Attendre le changement de repère. Vérifier que le mix reste sélectionné, la file classique conservée et la pause maintenue.
4. Vérifier à nouveau l'écran avant Précédent : le dernier rapport avait invalidé un clic qui touchait en réalité le shuffle classique après sortie du mix. Tester Précédent seulement lorsque la bonne commande est identifiée.
5. Refaire sans shuffle. Si possible, répéter quelques cycles Suivant/Précédent. Tester aussi le mix seul par l'UI normale, sinon le laisser NON TESTÉ ; aucun effacement de données pour fabriquer ce cas.

L'ancienne régression retirait le mix de la file après Suivant. Le nouveau transport doit conserver la sélection DJ côté contrôleur et service. Les commandes directes serveur acceptées/différées ne remplacent pas ce test de mix préparé.

## Mesures, limites et retour

Conserver captures, XML, titres, empreintes d'occurrences et logs bruts dans un dossier privé PC. Ne pas les publier ni les importer dans la mémoire. Pour les captures, vérifier le téléphone déverrouillé et la fenêtre réellement focalisée ; suivre les précautions de [la mission beta14](beta14-windows.md), sans employer son installateur. Noter les relevés PSS comme instantanés, pas comme pic instrumenté. Réaction UI, état prêt et premier son entendu sont des délais différents.

Les quarante clics Suivant beta14 ne validaient pas quarante fins naturelles. Android Auto réel, batterie, P95 et analyses DJ à froid restent NON TESTÉS/NON MESURÉS sans protocole réel. Ne pas relancer les sets hebdomadaires échoués ni modifier leurs préférences dans cette mission.

Remplir `beta15-report-template.md`, puis publier uniquement un bilan expurgé et ses limites dans `agent_context`, projet `downtify`, section `handoff`, sujet **beta15-usb-windows-results**. Préserver le rapport beta14 et le relais principal opencode. Exiger un reçu final vérifié ; sinon garder le rapport local marqué NON SYNCHRONISÉ. Aucun commentaire GitHub automatique demandé. Un seul agent pilote le téléphone ; opencode conserve les corrections de source.
