# Exécuter les tests intermods dans GitHub Actions

Le workflow **HomeLink integration tests** reste déclenché manuellement. Il ne
publie aucun mod, ne modifie aucune branche et ne se lance pas sur chaque push.
Les commits compatibles doivent déjà exister sur les sept dépôts distants.

## Révisions et versions

Fournir sept SHA complets de 40 caractères dans Actions :

| Champ | Dépôt | Checkout |
| --- | --- | --- |
| `homecore_sha` | `LKDM7/HomeCore` | `HomeCore` |
| `dashboard_sha` | `LKDM7/HomeLink-Dashboard` | `HomeLink` |
| `tasks_sha` | `LKDM7/HomeLink-Tasks` | `HomeLinkTask` |
| `farm_sha` | `LKDM7/HomeLink-Farm` | `FarmLink` |
| `energy_sha` | `LKDM7/HomeLink-Energy` | `HomeLinkEnergy` |
| `storage_sha` | `LKDM7/HomeLink-Storage` | `HomeLink Storage` |
| `quarry_sha` | `LKDM7/HomeLink-Quarry` | `HomeLinkQuarry` |

Les branches et tags ne sont pas acceptés. Le commit HomeCore contient le harnais
et l'API 1.9.0 / artefact 1.14.0. Chaque consommateur doit réellement déclarer
HomeCore 1.14.0 et Minecraft 1.21.1 ; ses métadonnées ne sont pas réécrites pour
faire passer le test. Les versions par défaut sont dans
[le guide du harnais](../integration-tests/README.md).

Le champ facultatif `consumer_versions` est un objet JSON, par exemple :

```json
{"dashboard":"1.6.0","tasks":"0.2.0","storage":"1.4.0"}
```

Seules les clés `dashboard`, `tasks`, `farm`, `energy`, `storage`, `quarry` sont
acceptées. Les versions sont validées puis passées comme arguments séparés
`-P<module>_version=<version>` aux deux lancements. Une version choisie doit
correspondre au `mod_version` du commit ; elle ne change aucune source.
Laisser `{}` pour utiliser les valeurs du harnais.

## Accès aux dépôts

Les checkouts utilisent le secret Actions facultatif `HOMELINK_REPOSITORIES_TOKEN`,
avec repli vers le token du workflow. Pour des dépôts privés appartenant à d'autres
projets, configurer dans HomeCore un token permettant de lire les sept dépôts
sélectionnés, avec la permission **Contents: read**. Le token du workflow peut
suffire pour les dépôts publics et le dépôt courant ; il n'accorde pas automatiquement
l'accès aux autres dépôts privés.

La configuration référence seulement le nom du secret. Ne placer aucune valeur
secret dans le code, les propriétés Gradle, les captures ou les logs.
`persist-credentials: false` évite de conserver ces identifiants dans les checkouts.
Aucun accès à GitHub Packages n'est requis pour HomeCore ou les consommateurs :
leurs vraies sources sont présentes localement et HomeCore vient du composite.

## Exécution et preuves

Le workflow vérifie chaque SHA après checkout et conserve la provenance, puis
installe Java 21. Depuis HomeCore, il exécute :

```sh
bash gradlew -p integration-tests check runGameTestServer --no-daemon --no-configuration-cache
xvfb-run -a -s '-screen 0 1280x720x24' bash gradlew -p integration-tests runClientSmoke --no-daemon --no-configuration-cache
```

Le smoke utilise Xvfb et le rendu logiciel Mesa (`LIBGL_ALWAYS_SOFTWARE=1`).
Les sources de production des six consommateurs sont compilées sans lancer leurs
builds natifs. Les intégrations JEI/REI restent compile-only. Les cinq GameTests
historiques gardent leurs scénarios ; Dashboard et Tasks sont aussi chargés sur
ce serveur. Le smoke client vérifie les sept mods, un écran du kit, les contrôles
vanilla et un frame réellement rendu. Il ne remplace pas les smokes métier des
consommateurs ni une revue visuelle complète de chaque screen.

Les résultats restent séparés des livrables :

- `homelink-integration-evidence` : SHA/provenance, versions optionnelles, logs serveur/client, capture du smoke, rapports et sommes SHA-256 HomeCore disponibles.
- `homecore-under-integration-test` : JAR HomeCore présents dans `HomeCore/build/libs`, sans publication de release.

Le harnais ne produit aucun JAR distribuable et ses fixtures ne sont pas incluses
dans HomeCore. Une exécution réussie ne valide que les sept commits de son rapport.
La présence du YAML ou de ce guide ne prouve pas qu'une validation distante a été
exécutée. Les anciens résultats HomeCore 1.10 restent historiques.

## English

The manual workflow checks out HomeCore and six consumers using seven immutable
40-character commit SHAs. `consumer_versions` optionally supplies a validated JSON
map of explicit version overrides. Actual Minecraft and HomeCore dependencies
must match the harness; consumer metadata is not rewritten.

For private cross-repository access, configure the Actions secret
`HOMELINK_REPOSITORIES_TOKEN` with read access to all selected repositories
(**Contents: read**). The workflow token is only a fallback; it does not grant
arbitrary access to other private repositories. Credentials are not persisted and
no secret values belong in source or evidence.

The workflow runs compiled UI-kit checks, the existing dedicated-server GameTests
with all six consumers, and a world-free client smoke under Xvfb/Mesa. Evidence
includes exact revisions, logs, the framebuffer capture and available reports.
Nothing is published. These checks complement native business-screen smokes and
validate only the specific commits actually executed.
