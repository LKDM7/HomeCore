# Exécuter les tests intermods dans GitHub Actions

Le workflow **HomeLink integration tests** est déclenché manuellement. Il ne se lance pas sur les pushes : les commits compatibles des consommateurs doivent d’abord exister sur leurs dépôts distants. Il ne publie aucun mod et ne change aucune branche.

Dans l’onglet **Actions** de HomeCore, sélectionner ce workflow puis fournir cinq SHA complets de 40 caractères :

| Champ | Dépôt |
| --- | --- |
| `homecore_sha` | `LKDM7/HomeCore` |
| `farm_sha` | `LKDM7/HomeLink-Farm` |
| `energy_sha` | `LKDM7/HomeLink-Energy` |
| `storage_sha` | `LKDM7/HomeLink-Storage` |
| `quarry_sha` | `LKDM7/HomeLink-Quarry` |

Les noms de branches et de tags ne sont pas acceptés. Le commit HomeCore choisi doit contenir `integration-tests` et l’API attendue par les quatre consommateurs. Les versions des consommateurs doivent correspondre aux exigences du fichier `integration-tests/build.gradle` de ce commit. Une modification locale non publiée n’est pas accessible au runner GitHub.

Les champs facultatifs `farm_version`, `energy_version`, `storage_version` et `quarry_version` permettent de choisir une version attendue différente du défaut du harnais. Une valeur fournie est transmise explicitement avec `-P<module>_version=<version>` et conservée dans le rapport. Elle doit correspondre au `mod_version` du commit concerné ; elle ne met pas à jour ses sources. Laisser ces champs vides pour tester les versions prévues par le harnais.

Le workflow vérifie chaque SHA après checkout, puis installe Java 21. Il reproduit le voisinage local `HomeCore`, `FarmLink`, `HomeLinkEnergy`, `HomeLink Storage` et `HomeLinkQuarry`. Depuis HomeCore, il exécute :

```sh
bash gradlew -p integration-tests runGameTestServer --no-daemon --no-configuration-cache
```

Le harnais compile les sources de production des quatre consommateurs sans lancer leurs builds et utilise HomeCore par build composite. Il ne passe pas par `publishToMavenLocal`. Les scénarios et les limites de couverture sont décrits dans [le guide du harnais](../integration-tests/README.md).

Les résultats restent séparés des livrables :

- `homelink-integration-evidence` contient les SHA exacts, la provenance du workflow, les journaux et les rapports disponibles, ainsi que les sommes SHA-256 des JAR HomeCore produits.
- `homecore-under-integration-test` contient uniquement les JAR HomeCore présents dans `HomeCore/build/libs`. Ce sont des artefacts de test, sans publication de release.

Le harnais ne produit aucun JAR distribuable ; ses fixtures ne sont pas jointes aux JAR HomeCore. Un workflow réussi valide uniquement les cinq commits inscrits dans son rapport. La présence de ce fichier YAML ne constitue pas une exécution réussie des tests distants.
