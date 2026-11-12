# Suivi des évolutions musicales

Ce dépôt conserve les modifications et leurs preuves indépendamment de leur acceptation dans le projet d’origine. Les branches de livraison restent disponibles ; les PR vers le projet d’origine sont une étape séparée. Une compilation ou une route présente ne suffit pas à qualifier une fonction sur appareil.

| Demande | État vérifié | Preuve ou prochaine validation |
| --- | --- | --- |
| Android Auto : file entière, doublons, aléatoire, pause | Beta9 publiée | 161 tests lecteur et 5 tests service ; validation voiture restante |
| DJ : précédent/suivant, refus visible, pause préservée | Client beta9 publié ; serveur candidat | Revue Android #1 ; validation serveur et appareil restante |
| Accueil : titres likés, playlist, recommandation du jour | Beta9 publiée | Historique local démarrant avec cette version |
| Accueil : Rock, Rap, Pop, Électro | En cours | Cartes ouvrant uniquement des sélections réellement disponibles |
| Personnalisation et diversité entre mix | En cours | Vérifier favoris, écoutes, skips et rotation des artistes |
| Trois états : désactivé, transitions 1–12 s, DJ bêta | À terminer | Libellés distincts, badge, chevauchement et animations réduites |
| Profils audio réutilisables | À qualifier | Couverture rythmique séparée de DCLAP, travaux incrémentaux et reprise |
| Démarrage classique puis relais DJ | À qualifier | Deux profils initiaux, décodage progressif et premier son mesuré |
| Sets DJ hebdomadaires audio | À terminer | Audio, repères, préparation reprenable, sauvegarde de copies immuables |
| Qualité des transitions et direct | À qualifier | 20 transitions multi-genres à niveau égal, heure continue et vitesse > 1,5× |

## Livraisons

- Beta9, code 18 : corrections lecteur et Android Auto. Revue : https://github.com/QuietForge7294/downtify-android/pull/1.
- Prochaine livraison : genres accessibles et mix davantage adaptés aux goûts, avec diversité vérifiée.

## Conditions de clôture

Conserver le même identifiant d’application et certificat ; augmenter le code de version ; produire une bêta R8 non débogable ; vérifier le téléchargement de l’APK publiée. Distinguer simulations, essais physiques, budgets configurés et mesures réelles. Ne pas présenter un fondu local comme du mixage rythmique, ni une sélection de titres comme un set DJ audio préparé. Les modules restent désactivés par défaut et les préférences ne sont pas modifiées silencieusement.

Le MCP mémoire conserve le suivi détaillé ; ce document et les issues/PR conservent les exigences et les résultats utiles aux revues publiques.
