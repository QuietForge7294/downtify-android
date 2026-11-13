# Beta12 — renouvellement des mixes

Dans « Pour vous », « Renouveler mes mixes » demande au serveur de reconstruire les sélections courantes avec les goûts et la rotation récente des artistes. Les nouveaux mixes se préparent en arrière-plan ; les copies enregistrées et la lecture active sont conservées. Le bouton apparaît seulement si le serveur annonce la capacité `playlist_refresh` et si une cadence est activée. Les fonctions restent optionnelles.

Les caches d’accueil ne peuvent pas conserver une réponse ancienne après le renouvellement ni être effacés par la réponse d’un compte précédent. Une déconnexion annule la demande même si d’anciennes capacités restent brièvement en mémoire. Les pochettes de chevauchement respectent aussi le réglage système de réduction des animations pour leur chargement.

APK : [0.4.0-beta12, code 21](https://partage.bardreau.ovh/d/HJmuJoX6Ju7B3L2/downtify-ai-0.4.0-beta12-20261113.apk), téléchargement vérifié. SHA-256 : `07bc2a20e5ca92a1197d78e6e4c2a0522a8fe980755d12360411375c47ad0893`. Même identifiant d’application et certificat que les bêtas précédentes, R8 actif, non débogable.

Validation : suites réseau et données passent ; trois tests de renouvellement couvrent réponse d’accueil en vol, changement de compte et déconnexion ; six tests d’interface Pour vous/chevauchement passent. Lint, Detekt et compilation optimisée passent. Aucun essai physique Android Auto ou téléphone n’est assimilé à ces tests locaux.

Complément serveur : [PR #6](https://github.com/QuietForge7294/downtify/pull/6), avec les genres et la personnalisation de la PR #3. Déploiement serveur restant : installer cet APK seul ne suffit pas à activer le renouvellement ou les nouveaux classements.
