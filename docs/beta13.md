# Beta13 — sets DJ hebdomadaires

L’accueil propose quatre sets audio DJ ; « Pour vous » présente les dix thèmes, dont Rock, Rap, Pop et Électro, avec état, progression, nombre de titres et durée. Un set prêt se lit dans le lecteur commun. « Enregistrer une copie » demande un nom et conserve l’audio accepté ainsi que ses repères. Les copies apparaissent dans la même page. La préparation ou la nouvelle tentative reste une action explicite lorsque le fichier n’est pas disponible.

Les nouvelles capacités sont optionnelles et absentes par défaut. Les fonctions exigent les autorisations effectives `weekly`, `dj_prepared` et `dj_weekly` ; le dernier réglage n’apparaît que sur un serveur compatible. Les changements de compte bloquent les requêtes périmées et leurs résultats. Quitter un set préparé libère la protection du cache sur un serveur compatible, sans supprimer son audio.

APK local vérifié : `0.4.0-beta13`, code 22, même identifiant et certificat que beta12, R8 actif, non débogable. Taille : 26 505 547 octets. SHA-256 : `942fdd3681739de0ec5d35fbab093e94f82666b01d949afe58317a92cb13572a`. L’envoi au service habituel de partage attend une confirmation explicite demandée par la revue automatique ; aucun téléchargement externe de cette version n’est annoncé comme vérifié.

Validation : 43 tests réseau et 162 tests lecteur réussis avant le dernier garde-fou de compte ; après celui-ci, cinq tests de renouvellement/compte et trois tests d’interface réussis. Detekt, Lint et assemblage bêta optimisé réussis sur les sources finales. Les tests couvrent action explicite, nom de copie, serveur ancien, désactivation, changement de compte et départ depuis un contrôleur système. Aucun essai physique téléphone/Android Auto n’est assimilé à ces tests locaux.

Complément serveur : [PR #10](https://github.com/QuietForge7294/downtify/pull/10). Le serveur compatible n’est pas encore déployé : installer cet APK seul ne fait pas apparaître ses nouveaux ensembles audio. Les genres et les goûts dépendent également des corrections serveur préservées dans les PR précédentes.
