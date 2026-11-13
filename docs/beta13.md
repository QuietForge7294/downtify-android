# Beta13 — sets DJ hebdomadaires

L’accueil propose quatre sets audio DJ ; « Pour vous » présente les dix thèmes, dont Rock, Rap, Pop et Électro, avec état, progression, nombre de titres et durée. Un set prêt se lit dans le lecteur commun. « Enregistrer une copie » demande un nom et conserve l’audio accepté ainsi que ses repères. La préparation ou la nouvelle tentative est explicite lorsque le fichier manque.

Les capacités restent optionnelles sur les anciens serveurs. Les ensembles DJ exigent les autorisations effectives `weekly`, `dj_prepared` et `dj_weekly`, cette dernière désactivée par défaut. Les changements de compte bloquent les requêtes périmées et leurs résultats. Quitter un set préparé libère sa protection du cache sur un serveur compatible, sans supprimer son audio.

APK : [0.4.0-beta13, code 22](https://partage.bardreau.ovh/d/D2fd5IzaaULSD3u/downtify-ai-0.4.0-beta13-20261113.apk), téléchargement vérifié octet pour octet le 13 novembre 2026. Même identifiant et certificat que beta12, R8 actif, non débogable. Taille : 26 505 547 octets. SHA-256 : `942fdd3681739de0ec5d35fbab093e94f82666b01d949afe58317a92cb13572a`. Partage créé pour 30 jours et 100 téléchargements.

Validation Android : 43 tests réseau et 162 tests lecteur réussis avant le dernier garde-fou de compte ; après celui-ci, cinq tests de renouvellement/compte et trois tests d’interface réussis. Detekt, Lint et assemblage bêta optimisé réussis. Ces résultats ne remplacent pas les essais physiques téléphone/Android Auto.

La version serveur intégrée `2cbc179` a été déployée et contrôlée le 13 novembre 2026 : quatre genres disponibles, renouvellement accepté, modèles prêts et préférences conservées. Les limites de ressources et les données natives ont été préservées. Deux sets synthétiques ont été produits dans l’image candidate avec un seul processus de modèle réutilisé et des originaux inchangés. Les ensembles DJ hebdomadaires restent désactivés par défaut.

Sources conservées : [PR Android #6](https://github.com/QuietForge7294/downtify-android/pull/6) et [PR serveur intégrée #11](https://github.com/QuietForge7294/downtify/pull/11). Les essais de qualité et de performance sur catalogue réel et téléphone/voiture restent à terminer.
