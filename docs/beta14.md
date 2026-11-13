# Beta14 — file complète, commandes DJ et minuterie

Cette livraison corrige le retour utilisateur sur beta13 : l’aléatoire pouvait ne lire que la fin de sa permutation lorsqu’il était activé après le démarrage d’un morceau. L’activation conserve désormais le morceau courant en tête, sans changer sa position. La restauration conserve séparément la permutation exacte enregistrée.

La bibliothèque passe au service par un jeton local, puis ses métadonnées se construisent hors du thread principal et s’installent par lots de 500. La lecture démarre avec le premier lot. L’ordre aléatoire porte sur toute la sélection, et les ajouts ne créent pas de nouvelles occurrences. Les doublons volontaires restent conservés. Une pause n’est jamais annulée par les lots suivants ; une nouvelle sélection ou un changement de compte invalide les anciens lots.

La navigation DJ intercepte aussi les contrôleurs qui traduisent suivant/précédent en changement d’index. La file classique conservée ne devient pas une cible implicite de cette navigation.

Le lecteur affiche un compte à rebours de la minuterie et son annulation. Les durées se choisissent dans une boîte dédiée. Paramètres → Lecture présente les modes Désactivé, Transitions et DJ, ainsi que la durée du fondu local de 1 à 12 secondes.

Le panneau DJ propose le direct progressif en premier et explique la différence avec la préparation d’un fichier complet. La préparation complète affiche l’avancement de l’analyse par titre. Les analyses manquantes peuvent toujours allonger le premier démarrage ; cette interface ne prouve pas un délai garanti.

Les nouveaux tests ont reproduit avant correction une file de 130 dont seulement 64 occurrences restaient accessibles et une sortie du DJ vers l’index classique suivant. La suite lecteur finale compte 172 tests réussis, sans échec ni test ignoré, dont les contrôleurs DJ avec et sans file classique, la navigation sous aléatoire, l’insertion d’un mix prêt et un catalogue synthétique de 28 001 titres. Les 17 tests d’interface ciblés (minuterie, réglages, modes) passent également. Spotless et Detekt passent. Android Lint et l’assemblage R8 passent également. Les tests physiques Android Auto, les performances sur téléphone et la batterie restent à vérifier.

## Vérifications sur téléphone

- Depuis une playlist de plus de 130 titres, lancer l’aléatoire et vérifier que toute la sélection figure dans la file, puis dépasser les anciens seuils de 10 et 33 titres.
- Dans Titres, lancer l’aléatoire sur toute la bibliothèque, puis sélectionner directement un titre proche de la fin. Vérifier le démarrage, le titre suivant, la réactivité et le remplissage progressif sans doublons ajoutés.
- Pendant le remplissage, mettre en pause ou choisir une autre playlist : la pause doit rester active et les anciens lots ne doivent plus remplacer la nouvelle sélection.
- Dans un mix DJ préparé, tester Suivant et Précédent depuis l’application et Android Auto, avec et sans file classique conservée, y compris lorsque l’aléatoire est actif. La navigation doit rester dans le mix.
- Mettre un mix direct prêt en prochain dans une file aléatoire : il doit réellement être le prochain élément joué.
- Activer une minuterie, vérifier le compte à rebours, puis l’annuler. Tester les réglages de transition sous Paramètres → Lecture.

Ces contrôles physiques complètent les tests automatisés ; ils ne sont pas déclarés effectués sans appareil connecté.

## Livraison vérifiée

[APK 0.4.0-beta14, code 23](https://partage.bardreau.ovh/d/IOckPzl3pkVx0Q3/downtify-ai-0.4.0-beta14-20261113.apk).
Identifiant `com.henriquesebastiao.downtify.debug`, non débogable, même certificat que beta13.
SHA-256 : `46c4abc2cacc19d8ad55e0d0f85b1f61fef028e4c87cb93a537b6ba50dc794bb`.
Téléchargement vérifié octet pour octet ; durée de partage de 30 jours et limite initiale de 100 téléchargements.
Sources fonctionnelles : commit `9e5d3ea`, [PR #8](https://github.com/QuietForge7294/downtify-android/pull/8), reposant sur la protection des builds en PR #7.

Le build APK seul a réussi en 343,572 s avec un pic cgroup de 3 756 191 744 octets (3,50 Gio), sous le plafond de 4 Gio. Les tests, l’assemblage et Lint sont lancés séparément ; voir [le budget des builds](android-build-budget.md).

Le direct progressif privilégie le démarrage sans attendre le fichier préparé complet. Cela ne résout pas à lui seul le coût des analyses à froid et ne constitue pas une mesure du premier son sur téléphone.
