# Qualification TV-FIRST 008 locale

Le gate `gate.py` refuse tout changement hors du manifeste fini, toute divergence des deux bases et toute source non committée. Le commit local Android et le commit local Cloud doivent chacun être l’unique descendant direct de leur BASE. Aucun remote n’est contacté. Les sceaux 003/006 restent inchangés.

Après application des patches sur leurs bases exactes et commits locaux de provenance :

```bash
python3 -B qualification/tvfirst008/gate.py --cloud /chemin/checkout-cloud
python3 -B qualification/tvfirst008/test_gate.py -v
python3 -B qualification/tvfirst008/gate.py --cloud /chemin/checkout-cloud --evidence /chemin/preuves
```

Le manifeste porte les hashes exacts des deux deltas ; seul le littéral de hash du gate est normalisé pour casser l’auto-référence, comme le modèle de scellement antérieur. Le hash brut du manifeste est ensuite fixé dans le gate. Les commits et arbres finaux sont conservés dans le manifeste de provenance de livraison.

La qualification conjointe requiert un PostgreSQL natif jetable en boucle locale et l’activation explicite du peer **de test** : `SCENEVIBE_TVFIRST008_ANDROID_NATIVE=enabled`. Exécuter le fichier `backend-tests/tvfirst008-android-peer.test.ts` seul avec Vitest. Celui-ci démarre les handlers de production sur une adresse loopback et appelle Gradle pour quatre classes natives `TvFirst008*` sur API31/35 et six cas purs du transport HTTPS. L’absence du peer est un échec JVM, pas un skip accepté. Le fichier doit rester hors des suites de qualification ne fournissant pas ce prérequis.

Les contrats Cloud se qualifient séparément avec le vrai PostgreSQL et sans parallélisme entre fichiers de tests qui préparent l’extension partagée `pgcrypto`. La concurrence métier reste testée dans les cas eux-mêmes. Ne pas rendre les migrations historiques permissives pour résoudre une collision de préparation des schémas.

Le gate lit les cas réels et les échecs de suite/hooks, pas seulement `numFailedTests`. Il refuse les résultats 008 manquants, ignorés ou incomplets. Le contrôle historique JVM est exécuté sur les résultats réellement compilés du code courant ; l’unique skip historique est identifié séparément par profil.

Aucun workflow distant n’est ajouté ou sélectionné par cette livraison. La publication future demande un nouveau mandat incluant le risque de Preview Vercel et la sélection CI du gate 008. Aucun résultat de cet outil n’autorise MEDIA, rendu ou Sony.
