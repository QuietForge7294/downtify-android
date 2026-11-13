# Suivi des évolutions musicales

Ce dépôt conserve les modifications et leurs preuves indépendamment de leur acceptation dans le projet d’origine. Les branches de livraison restent disponibles ; les PR vers le projet d’origine sont une étape séparée. Une compilation ou une route présente ne suffit pas à qualifier une fonction sur appareil.

| Demande | État vérifié | Preuve ou prochaine validation |
| --- | --- | --- |
| Android Auto : file entière, doublons, aléatoire, pause | Beta9 publiée | 161 tests lecteur et 5 tests service ; validation voiture restante |
| DJ : précédent/suivant, refus visible, pause préservée | Client beta9 publié ; serveur candidat | Revue Android #1 ; validation serveur et appareil restante |
| Accueil : titres likés, playlist, recommandation du jour | Beta9 publiée | Historique local démarrant avec cette version |
| Accueil : Rock, Rap, Pop, Électro | Client beta13 livré ; serveur intégré déployé | Quatre genres prêts ; pertinence à apprécier à l’écoute |
| Personnalisation et diversité entre mix | Serveur intégré déployé ; client beta13 livré | Renouvellement et diversité contrôlés ; appréciation à l’écoute restante |
| Trois états : désactivé, transitions 1–12 s, DJ bêta | Beta11 publiée | 33 tests de modes/interface passent ; badge, chevauchement réel et mouvement réduit |
| Profils audio réutilisables | Serveur déployé, vrais modèles testés | Préanalyse incrémentale en place ; couverture progressive, validation catalogue restante |
| Démarrage classique puis relais DJ | Code déployé ; performances à qualifier | Deux profils initiaux et décodage progressif ; premier son et latences réelles à mesurer |
| Sets DJ hebdomadaires audio | Client beta13 livré et serveur déployé | Dix thèmes et copies nommées ; option hebdomadaire désactivée par défaut, essais physiques restants |
| Qualité des transitions et direct | À qualifier | 20 transitions multi-genres à niveau égal, heure continue et vitesse > 1,5× |

## Livraisons

- Beta9, code 18 : corrections lecteur et Android Auto. Revue : https://github.com/QuietForge7294/downtify-android/pull/1.
- Beta10, code 19 : cartes Rock/Rap/Pop/Électro ; PR Android #3. Complément serveur en PR QuietForge7294/downtify#3, déploiement restant.
- Beta11, code 20 : trois choix de mode, badge au-dessus de la pochette et deux pochettes pendant le chevauchement réel. Validation physique restante.
- Beta12, code 21 : renouvellement explicite des mixes personnels, cache protégé contre les changements de compte et réponses périmées ; réduction du mouvement appliquée aussi aux pochettes entrantes. Voir [les notes](beta12.md). Complément serveur en PR #6, non déployé.
- Beta13, code 22 : ensembles audio DJ hebdomadaires et copies nommées. APK publiée et téléchargement vérifié ; serveur correspondant déployé. Voir [les notes](beta13.md). Version serveur intégrée en PR #11, déployée le 13 novembre 2026.

## Conditions de clôture

Conserver le même identifiant d’application et certificat ; augmenter le code de version ; produire une bêta R8 non débogable ; vérifier le téléchargement de l’APK publiée. Distinguer simulations, essais physiques, budgets configurés et mesures réelles. Ne pas présenter un fondu local comme du mixage rythmique, ni une sélection de titres comme un set DJ audio préparé. Les modules restent désactivés par défaut et les préférences ne sont pas modifiées silencieusement.

Le MCP mémoire conserve le suivi détaillé ; ce document et les issues/PR conservent les exigences et les résultats utiles aux revues publiques.
