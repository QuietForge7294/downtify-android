# Bêta16 — publication Bluetooth et repères DJ

Les essais USB de bêta15 ont confirmé deux chargements successifs de la grande bibliothèque et une pause maintenue pendant les lots. Ils ont aussi reproduit un OOM du processus Bluetooth dans la désérialisation de `MediaSession.QueueItem`, un repère DJ incorrect après une navigation en pause et une réduction de la file classique après arrêt volontaire du mode DJ. Les bruts du téléphone restent privés.

## File interne et session Android historique

La file complète reste dans le lecteur et accessible au contrôleur de Downtify. La publication massive de `QueueItem` est retirée de la session Android historique, partagée notamment avec Bluetooth. Cette session garde les métadonnées courantes et les commandes de transport. Sa liste standard de file n'est plus diffusée : une liste système vide n'indique donc pas une file interne vide. Le parcours de bibliothèque paginé reste distinct ; le comportement d'une vraie unité Android Auto doit être recontrôlé.

Cette séparation utilise les commandes disponibles du contrôleur de notification prévues par Media3, sans réflexion en production ni changement du système Bluetooth. Le contrôleur est connecté explicitement ; restauration et installations de file attendent l'application de ces permissions. Les requêtes de contrôleurs ont une attente bornée. La restauration suspendue reste annulable avec le service et n'efface pas le dernier état sauvegardé.

Un compteur dans l'écran de file de Downtify montre le nombre total d'éléments internes, y compris ceux déjà joués et le rendu DJ éventuel. Ce compteur ne prétend pas connaître le total indépendant du catalogue serveur.

Le test d'origine via `MediaController.getQueue()` n'était pas probant dans Robolectric, dont les objets de session et de contrôleur Android ne sont pas reliés comme sur appareil. Le test corrigé observe le point de publication réel de compatibilité de Media3 en laissant passer les appels inchangés : il a reproduit la diffusion de 1 501 éléments synthétiques avant protection. Un contrôle positif conserve ce comportement dans une session non protégée pour vérifier l'observateur. Les tests de protection vérifient séparément la file complète de l'application, les publications historiques vides, le transport et l'ordre de configuration au démarrage. Ils ne simulent pas le heap réel du processus Bluetooth.

Références de la version utilisée : [publication legacy Media3 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionLegacyStub.java), [configuration du contrôleur de notification](https://developer.android.com/reference/androidx/media3/session/MediaSession).

## Repères et sortie DJ

Les cibles de navigation rejoignent désormais les frontières fractionnaires à la milliseconde supérieure, au lieu de tomber juste avant la fin du repère précédent. La sélection de repère est partagée avec le suivi des métadonnées ; une mise à jour limitée aux extras n'est plus écartée à cause de l'égalité de métadonnées de Media3. Les tests emploient des repères fractionnaires qui se chevauchent et maintiennent la pause pendant la navigation.

Le retour classique s'effectue dans le service sur la file complète. Quand des titres classiques sont conservés, il retire uniquement les rendus DJ et préserve les occurrences et la permutation. Il reprend la position classique sauvegardée si le titre audible du mix n'appartient pas à cette file. Les titres du rendu ne sont reconstruits qu'en l'absence de file classique conservée.

## Validation et qualification

Livraison du 14 novembre 2026 : [APK 0.4.0-beta16/code25](https://partage.bardreau.ovh/d/4em660AR9mPKEM6/downtify-ai-0.4.0-beta16-20261114.apk), 26 522 591 octets. SHA-256 : `ef4d215391168c92851ffdfd69466c963dd67a63318156c17774e1cff3304a99`. Le téléchargement a été comparé octet pour octet au fichier signé. Le certificat est identique à celui des bêtas précédentes ; l'APK est optimisée par R8 et non débogable.

Les 187 tests du lecteur passent. Spotless et Detekt passent. Étapes séparées sous le budget global de 4 Gio : tests 44,624 s / pic 2,03 Gio ; compilation APK 67,557 s / 2,17 Gio ; assemblage R8 348,657 s / 3,44 Gio ; Lint 106,740 s / 2,16 Gio. Ces pics concernent le groupe complet de processus du build. Aucune augmentation des plafonds ni nouveau build concurrent. Ces mesures ne prouvent pas un gain de performances du téléphone.

Le recontrôle Windows est décrit dans [la mission bêta16](testing/beta16-windows-retest.md). La PR reste en brouillon tant que la qualification physique est incomplète. La confirmation sur téléphone reste nécessaire, en particulier pour l'absence de nouveau crash Bluetooth pendant un chargement et une restauration de grande file, ainsi que les repères DJ en pause. Aucun réglage Bluetooth, données d'application, préférence serveur ou budget de build n'est modifié par cette livraison.
