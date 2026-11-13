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

La suite lecteur compte 178 tests réussis, sans échec ni test ignoré. Elle couvre dix cas de transfert et six cas de contrôleur DJ, dont la sélection provisoire côté client. Les deux régressions décrites ont été reproduites avant correction. Les preuves de l’APK sont ajoutées après compilation. Les confirmations physiques de beta14 restent partielles ; cette correction doit être retestée sur le téléphone, en particulier avec deux sélections successives du même contexte et un mix préparé en pause, avec puis sans aléatoire.

Aucun changement des préférences, des signatures, du serveur ou des budgets de build ne fait partie de cette livraison. Les erreurs de préparation de certains sets hebdomadaires observées sur téléphone restent à diagnostiquer séparément. L'analyse DJ à froid, les mesures de premier son et la batterie ne sont pas qualifiées par ces corrections.
