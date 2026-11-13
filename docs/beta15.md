# Beta15 — corrections issues des essais USB beta14

Les essais sur téléphone ont validé la file d'une grande playlist, la navigation par quarante commandes Suivant, le décompte et l'expiration de la minuterie, ainsi que l'accès aux réglages de lecture. Ils ont aussi reproduit une sortie du DJ préparé vers un morceau classique et observé une bibliothèque qui restait à 500 éléments. Les fins naturelles après 33 titres et Android Auto réel n'étaient pas qualifiés.

## Navigation DJ depuis l'application

`MediaController.seekToNext()` peut publier immédiatement une sélection classique provisoire côté client avant que la commande arrive au service. Le service savait déjà interpréter Suivant comme un déplacement dans les repères DJ, mais le coordinateur de l'application pouvait entre-temps interpréter cette sélection provisoire comme une sortie du mix et retirer celui-ci de la file.

Les commandes Suivant/Précédent de l'application passent désormais par une commande de session dédiée pendant un mix DJ. Elle cible le mix courant et agit sur son transport dans le service sans produire cette sélection classique provisoire. La navigation classique conserve ses commandes habituelles ; les contrôleurs externes gardent le transport du service.

Le nouveau test observant l'état immédiat du contrôleur a échoué avant correction : mix attendu, morceau classique observé. Les tests beta14 n'observaient que le lecteur du service après traitement ; ils ne couvraient pas cet état intermédiaire.

## Chargements successifs de la bibliothèque

Le transfert utilisait un jeton dans les extras des métadonnées de playlist pour reconnaître sa file. Dans la version de Media3 utilisée, une modification limitée au contenu des extras peut être considérée égale et ignorée. Une seconde sélection du même contexte conservait alors l'ancien jeton et interrompait son transfert au deuxième lot.

Le test ajouté reproduit avant correction un second chargement de 1 501 éléments qui reste à 500, avec l'avant-dernier titre à l'index 498. Cet index seul ne permettait donc pas de déduire la taille de la sélection initiale sur téléphone.

L'identité du transfert suit désormais une génération interne de file, indépendante des métadonnées d'affichage. Les ajouts par lots et les mises à jour de pochette ne l'invalident pas ; un remplacement ou effacement de la file interrompt l'ancien chargement.

## Qualification et livraison

La suite lecteur compte 178 tests réussis, sans échec ni test ignoré. Elle couvre dix cas de transfert et six cas de contrôleur DJ, dont la sélection provisoire côté client. Les deux régressions décrites ont été reproduites avant correction. Spotless, Detekt, Android Lint et la génération R8 passent également. Les confirmations physiques de beta14 restent partielles ; cette correction doit être retestée sur le téléphone, en particulier avec deux sélections successives du même contexte et un mix préparé en pause, avec puis sans aléatoire.

Aucun changement des préférences, des signatures, du serveur ou des budgets de build ne fait partie de cette livraison. Les erreurs de préparation de certains sets hebdomadaires observées sur téléphone restent à diagnostiquer séparément. L'analyse DJ à froid, les mesures de premier son et la batterie ne sont pas qualifiées par ces corrections.

## APK publiée

[Bêta15/code24](https://partage.bardreau.ovh/d/161J09Gjcz9reUn/downtify-ai-0.4.0-beta15-20261113.apk), identifiant `com.henriquesebastiao.downtify.debug`, non débogable, même certificat que beta14.
SHA-256 : `142d71d5c80fa46a74f1653d01988bc126f4dac18004d07061e63afe0f42b214` ; 26505547 octets. Téléchargement vérifié octet pour octet, durée initiale de partage de 30 jours et 100 téléchargements à création.

Source fonctionnelle `f592890`, [PR #9](https://github.com/QuietForge7294/downtify-android/pull/9), [mission Windows de recontrôle](testing/beta15-windows-retest.md). L'agent PC doit utiliser l'installateur beta15, pas celui de beta14. Les résultats doivent rester distincts dans le sujet mémoire `beta15-usb-windows-results`.

Mesures sous le budget inchangé de 4 Gio : exécution des 178 tests : 49,944 s / pic 2,33 Gio après compilation séparée ; préparation APK : 71,936 s / pic 2,34 Gio ; assemblage : 433,756 s / pic 3,49 Gio ; Lint : 114,207 s / pic 2,37 Gio. Les étapes ont des caches et des charges différents : aucune comparaison de vitesse à froid n'est déduite de ces nombres.
