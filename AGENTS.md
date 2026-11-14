# Builds Android sur opencode

Sur opencode (serveur de développement), toute commande Gradle doit passer par
`./gradlew` protégé ou `/usr/local/bin/downtify-android-build --wrapper /chemin/gradlew -- TÂCHES`.
Le verrou et le cgroup sont communs aux chats et worktrees. Ne pas contourner un
refus de verrou, la vérification de marge mémoire ou les plafonds. Code 75 :
build en cours, pression excessive ou échec précédent à examiner ; aucune boucle de relance.

Budget global 4 Gio, Gradle 2 Gio, Kotlin 1 Gio, deux workers au maximum,
parallélisme désactivé, JDK du lanceur. Aucun daemon de build ne doit survivre
à son unité. Lire le reçu après un échec, notamment OOM, avant toute adaptation.
Ne pas augmenter les plafonds pour obtenir du vert sans mesure et justification.

Après une modification Kotlin, séparer la compilation, les tests et R8 en
invocations protégées distinctes pour libérer le compilateur entre les étapes.
Lire les commandes et mesures dans `docs/android-build-budget.md`.

Préserver les caches, APK, signatures et modifications des autres travaux.
Pas de `clean`, purge des dépendances ou émulateur par défaut. Un seul travail lourd.
Les instructions SSH/PVE et d’audit du propriétaire restent applicables.
Procédure et retour arrière : `docs/android-build-budget.md`.

# Livraison et qualification USB réutilisables

Lire `docs/testing/automation.md` avant une livraison ou une campagne téléphone.
Les scripts partagés sont dans `tools/automation/` ; leurs tests ne lancent ni Gradle ni ADB.
Regrouper la qualification physique après les changements constituant le candidat final.
Réutiliser les reçus réussis de sources identiques et les cas USB terminés ; ne pas réinstaller
ou rejouer une campagne pour un simple compte rendu. Un crash, une étape interrompue ou un
code 75 impose l'examen des preuves, sans boucle de relance. Un timeout d'observation ne
prouve pas l'arrêt du build : contrôler l'unité existante avant toute autre construction.
Un seul build lourd protégé et un seul pilote USB ; seules les lectures et vérifications
légères indépendantes peuvent être parallélisées. Ne pas publier les preuves privées.
