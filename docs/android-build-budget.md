# Builds Android bornés sur opencode

Applicable au serveur de développement opencode, le 13 novembre 2026. L’incident rapporté
étaye une contention mémoire/entrées-sorties ; il ne prouve ni une fuite ni la
responsabilité exclusive d’un processus. Aucun accès à Proxmox, changement de ZRAM,
de limites LXC ou de VM ne fait partie de cette correction.

## Commande obligatoire

Dans un checkout installé : `./gradlew :app:assembleBeta --console=plain`.
Pour un nouveau checkout dont le wrapper n’a pas encore le relais :

```sh
/usr/local/bin/downtify-android-build --wrapper /chemin/absolu/android/gradlew -- :app:assembleBeta --console=plain
```

Le lanceur refuse immédiatement un second build (code 75), avant toute JVM.
Le fichier `/run/lock/downtify-android-build.lock` et l’unité systemd fixe
`downtify-android-build.service` protègent tous les chats/worktrees. Un init-script
Gradle utilisateur refuse les appels directs Downtify hors de cette unité.
Il s’agit d’une protection contre les lancements accidentels, pas d’une frontière
de sécurité contre un utilisateur root déterminé à la contourner.

## Budget

| Ressource | Limite |
| --- | --- |
| Groupe complet, y compris enfants et cache imputé au groupe | 4 Gio maximum, seuil haut 3,5 Gio |
| Heap Gradle | 2 Gio |
| Heap Kotlin | 1 Gio |
| Heap par JVM de test | 512 Mio ; un fork par tâche de test |
| Workers Gradle | 2 maximum, parallélisme des projets désactivé |
| CPU du groupe | 2 CPU au maximum |
| Swap du groupe | 0 ; aucune modification du swap/ZRAM global |
| Processus et threads du groupe | 256 maximum |
| Durée maximale d’un build | 20 minutes |

Les heaps ne sont pas des estimations de RSS. Le plafond mémoire du cgroup couvre
leur utilisation cumulée, métadonnées, mémoire native, outils et cache imputé au
groupe. La slice commune impose aussi ce plafond global à ses unités enfants.
Le lanceur refuse l’admission si `MemAvailable < 5 Gio` ou si la pression mémoire
complète sur dix secondes dépasse 5 %. Le LXC déclaré de 10 Gio conserve ainsi
une allocation maximale de build de 4 Gio ; la marge disponible est vérifiée à
chaque lancement. Une pression I/O complète sur dix secondes supérieure à 15 %
refuse aussi l’admission. Cela ne garantit pas la capacité de l’hôte sous une pression
créée par d’autres VM.

Le JDK est épinglé à l’installation existante `/opt/jdk/jdk-17.0.21+9`. Le cache
commun `/root/.gradle` et les dépendances sont réutilisés. Gradle est lancé en
`--no-daemon` ; sa JVM de durée de build peut toujours être appelée « single-use
daemon ». Les enfants Kotlin restants sont arrêtés dans le seul cgroup du build,
puis systemd applique `KillMode=control-group`. Aucun `pkill java` global.

Les paramètres mémoire/CPU, le JDK et le nombre de workers sont imposés par le
lanceur. Les surcharges de heap dans les variables Java/Gradle sont refusées sans
afficher leur valeur. Les secrets de signature éventuellement présents sont
transmis par nom de variable à systemd, jamais dans les arguments ou reçus.

## Reçus et échecs

`/var/lib/downtify-build-guard/latest.json` indique le dernier répertoire de mesure.
Chaque reçu privé contient durée, pic mémoire cgroup, somme RSS observée séparée,
JVM/roles/heaps, pression mémoire/I/O, événements mémoire et code de retour.
Les arguments arbitraires et valeurs d’environnement ne sont pas enregistrés.
Le reçu est actualisé pendant le build afin de conserver une mesure même si le
groupe est arrêté pour dépassement. Aucun échec ne lance une nouvelle tentative.

Après un OOM, livrer les mesures et examiner le reçu avant toute augmentation.
Une unité échouée bloque les lancements suivants. Après examen explicite :
`systemctl reset-failed downtify-android-build.service`, puis un lancement manuel.
Cette commande ne relance aucun build.

## Installation et retour arrière

Sources : `tools/build-guard/`. L’installation conserve les préimages de ses seuls
fichiers de configuration dans `/var/lib/downtify-build-guard`, permissions privées.
Les neuf wrappers présents et leurs propriétés sont couverts, ainsi que les
propriétés/init-script Gradle utilisateur et la slice. Les autres paramètres des
fichiers existants sont conservés. Les APK, keystores et caches ne sont pas copiés
ni supprimés par l’installation.

Hors build actif, `python3 tools/build-guard/rollback.py` restaure uniquement les
fichiers correspondant encore aux empreintes installées. Une modification plus
récente bloque le retour arrière pour préserver le travail concerné. Il arrête
la seule slice de build, restaure/supprime ses fichiers gérés puis recharge
systemd. Il ne modifie pas les limites du LXC ou de l’hôte.

## Mesure du 13 novembre 2026

Assemblage bêta R8 avec caches réutilisés : succès, code 0, 676,566 secondes
(11 min 16 s), 217 tâches dont 198 à jour. Pic mémoire du cgroup complet :
3 760 115 712 octets, soit 3,50 Gio. Trois JVM observées : lanceur 64 Mio,
Gradle 2 Gio et Kotlin 1 Gio de heap maximal. Aucun worker JVM de test n’est
attendu dans cet assemblage ; les deux workers Gradle restent un plafond configuré.
Événements `max=0`, `oom=0`, `oom_kill=0`. Les processus enfants restants ont été
nettoyés dans la seule unité de build ; les anciens daemons identifiés sont absents.

Le seuil souple initial de 3 Gio causait de forts ralentissements de récupération
mémoire dans le groupe (pression complète sur dix secondes observée à 74,55 %),
avec peu de pression I/O. Il a été porté à 3,5 Gio pendant cette mesure, sans
modifier le plafond dur de 4 Gio ni les heaps. En fin de build : pression mémoire
complète sur dix secondes 0 %, pression I/O complète 1,15 %. La durée ci-dessus
inclut cette phase de réglage ; elle ne démontre pas un gain de vitesse par
rapport à un build comparable avant/après.

Un lancement `gradlew help` depuis un autre checkout pendant le build a été
refusé avec le code 75, avant toute JVM. Aucun second assemblage lourd n’a été
lancé pour ce contrôle. Le reçu complet et les préimages restent privés dans
`/var/lib/downtify-build-guard`. Aucun déploiement n’a été effectué pour cette
intervention. Un build réussi ne constitue pas une validation fonctionnelle
du téléphone ou d’Android Auto.

## Séparer les étapes de validation

Lancer tests, assemblage APK et Lint dans des invocations protégées distinctes,
successivement, après vérification de chaque résultat. Ne pas les regrouper dans
une JVM de livraison unique. Exemple :

```sh
./gradlew --offline :core:player:testDebugUnitTest --console=plain
./gradlew --offline :app:assembleBeta --console=plain
./gradlew --offline :app:lintBeta --console=plain
```

Ces lignes représentent trois étapes manuelles ; un refus ou échec interrompt la
procédure et doit être examiné avant la suivante. Aucune relance en boucle.

Lors de la validation beta14, une invocation regroupant tests d’interface, Lint
et assemblage a atteint 3 975 Mio environ, avec pression mémoire complète du
groupe à 73,54 % sur dix secondes et avertissement Metaspace. Elle a été arrêtée
explicitement après 649,825 s : code 143, aucun événement OOM ni dépassement dur.
Seule l’unité du build concerné a été arrêtée ; aucun plafond n’a augmenté.

L’assemblage seul, avec les caches déjà alimentés, a ensuite réussi en
343,572 s : pic 3 756 191 744 octets (3,50 Gio). Lint séparé a réussi en
109,503 s : pic 2 395 529 216 octets (2,23 Gio). Cette comparaison guide le
séquençage ; les caches et tâches diffèrent, elle ne démontre pas un gain de
vitesse à charge identique. Les plafonds restent inchangés.
