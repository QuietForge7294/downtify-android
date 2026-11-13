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
