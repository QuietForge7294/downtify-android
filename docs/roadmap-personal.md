# Suivi des évolutions musicales

Ce dépôt conserve les modifications et leurs preuves indépendamment de leur acceptation dans le projet d’origine. Les branches de livraison restent disponibles ; les PR vers le projet d’origine sont une étape séparée. Une compilation ou une route présente ne suffit pas à qualifier une fonction sur appareil.

| Demande | État vérifié | Preuve ou prochaine validation |
| --- | --- | --- |
| Android Auto : file entière, doublons, aléatoire, pause | Beta9 publiée | 161 tests lecteur et 5 tests service ; validation voiture restante |
| DJ : précédent/suivant, refus visible, pause préservée | Client beta9 publié ; serveur candidat | Revue Android #1 ; validation serveur et appareil restante |
| Accueil : titres likés, playlist, recommandation du jour | Beta9 publiée | Historique local démarrant avec cette version |
| Accueil : Rock, Rap, Pop, Électro | Client beta10 publié ; serveur PR #3 | Déploiement serveur et vérification du catalogue restants |
| Personnalisation et diversité entre mix | Serveur PR #3 et #6 ; client beta12 publié | Favoris, écoutes, skips, rotation et renouvellement explicite ; déploiement restant |
| Trois états : désactivé, transitions 1–12 s, DJ bêta | Beta11 publiée | 33 tests de modes/interface passent ; badge, chevauchement réel et mouvement réduit |
| Profils audio réutilisables | Serveur PR #5, testé localement | 3606 tests backend passent avant le lot de renouvellement ; deux essais avec modèles réels restent exclus ; déploiement restant |
| Démarrage classique puis relais DJ | À qualifier | Deux profils initiaux, décodage progressif et premier son mesuré |
| Sets DJ hebdomadaires audio | À terminer | Audio, repères, préparation reprenable, sauvegarde de copies immuables |
| Qualité des transitions et direct | À qualifier | 20 transitions multi-genres à niveau égal, heure continue et vitesse > 1,5× |

## Livraisons

- Beta9, code 18 : corrections lecteur et Android Auto. Revue : https://github.com/QuietForge7294/downtify-android/pull/1.
- Beta10, code 19 : cartes Rock/Rap/Pop/Électro ; PR Android #3. Complément serveur en PR QuietForge7294/downtify#3, déploiement restant.
- Beta11, code 20 : trois choix de mode, badge au-dessus de la pochette et deux pochettes pendant le chevauchement réel. Validation physique restante.
- Beta12, code 21 : renouvellement explicite des mixes personnels, cache protégé contre les changements de compte et réponses périmées ; réduction du mouvement appliquée aussi aux pochettes entrantes. Voir [les notes](beta12.md). Complément serveur en PR #6, non déployé.

## Conditions de clôture

Conserver le même identifiant d’application et certificat ; augmenter le code de version ; produire une bêta R8 non débogable ; vérifier le téléchargement de l’APK publiée. Distinguer simulations, essais physiques, budgets configurés et mesures réelles. Ne pas présenter un fondu local comme du mixage rythmique, ni une sélection de titres comme un set DJ audio préparé. Les modules restent désactivés par défaut et les préférences ne sont pas modifiées silencieusement.

Le MCP mémoire conserve le suivi détaillé ; ce document et les issues/PR conservent les exigences et les résultats utiles aux revues publiques.
