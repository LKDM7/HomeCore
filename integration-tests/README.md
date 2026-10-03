# Tests d'intégration HomeLink

Ce harnais charge HomeCore et les six consommateurs HomeLink ensemble : Dashboard,
Storage, Farm, Quarry, Energy et Tasks. Il compile leurs sources et ressources de
production dans des source sets séparés sans modifier les dépôts voisins ni lancer
leurs builds. Les cinq GameTests historiques conservent leur logique de transferts
et de permissions ; l'ajout de Dashboard et Tasks vérifie aussi leur chargement sur
le même serveur dédié.

## Versions et chemins explicites

| Module | Version attendue | Dossier voisin |
| --- | --- | --- |
| HomeCore | 1.14.0 / API 1.9.0 | `..` (build composite parent) |
| Dashboard | 1.6.0 | `../../HomeLink` |
| Storage | 1.4.0 | `../../HomeLink Storage` |
| Farm | 1.5.0 | `../../FarmLink` |
| Quarry | 1.4.0 | `../../HomeLinkQuarry` |
| Energy | 0.5.0 | `../../HomeLinkEnergy` |
| Tasks | 0.2.0 | `../../HomeLinkTask` |

Minecraft 1.21.1, NeoForge 21.1.252, Java 21. Les noms de paramètres sont
`dashboard_dir/version`, `storage_dir/version`, `farm_dir/version`,
`quarry_dir/version`, `energy_dir/version`, `tasks_dir/version` (chaque propriété
se passe séparément avec `-P`). Le harnais refuse un checkout dont la version réelle
ne correspond pas à celle demandée. Il vérifie aussi la version Minecraft et les
vraies dépendances HomeCore exactes et minimales des consommateurs ; il ne réécrit
plus leurs métadonnées pour simuler une migration.

Parchment 1.21.1 / 2024.11.17 aligne la compilation des sources Dashboard et Tasks.
JEI, REI, Fabric annotations et, pour Storage, Architectury sont uniquement des
dépendances de compilation de leurs intégrations optionnelles. Aucun de ces mods
optionnels n'est ajouté au runtime du harnais. HomeCore vient du composite local ;
aucun registry distant ou Maven local n'est publié par ce projet.

## Exécution

Depuis HomeCore :

```powershell
.\gradlew.bat -p integration-tests classes check
.\gradlew.bat -p integration-tests runGameTestServer
.\gradlew.bat -p integration-tests runClientSmoke
.\gradlew.bat -p integration-tests runClient
```

Sous Linux/macOS, utiliser `./gradlew`. `runClient` charge les six mods pour une
revue manuelle commune ; ce lancement n'est pas un smoke automatique et n'annonce
aucun résultat visuel. Les smokes natifs de chaque consommateur restent nécessaires
pour couvrir leurs screens, focus clavier, petites fenêtres et FR/EN.

`runClientSmoke` attend la fin du chargement des ressources au menu principal,
vérifie que les six consommateurs et HomeCore sont chargés, puis ouvre un petit
écran utilisant le kit partagé sans créer de monde. Il teste l'activation clavier,
la tabulation et la saisie du champ vanilla. Après un véritable frame rendu, il
vérifie un pixel `BACKGROUND`, écrit une capture PNG et exige le marqueur
`HOMELINK_INTEGRATION_CLIENT_OK`. Cela vérifie le chargement commun et le kit,
pas les screens métier de chaque mod. Le fixture est enregistré uniquement avec
`Dist.CLIENT` ; le serveur dédié ne doit pas le charger.

`check` inclut `verifyUiKitConsumers` : inspection des champs constants des classes
compilées pour détecter une palette complète de shell copiée, même si les champs
ont été renommés. Le contrôle autorise les couleurs métier isolées et exige des
références réelles à `HomeLinkUi` ou `HomeLinkButton` dans chaque consommateur.
Il ne dépend ni des commentaires ni des noms de variables locales.

Les journaux sont dans `integration-tests/build/server/logs` et
`integration-tests/build/client/logs`. Ce projet n'est pas un mod distribuable ;
son task `jar` est désactivé.
Les preuves du smoke sont dans `integration-tests/build/client-smoke/logs` et
`screenshots`.

## Couverture des cinq GameTests

- Production réelle d'un panneau Energy et transfert vers une station Farm, avec conservation des HE.
- Sortie FarmBot Station vers un vrai Storage Deposit et respect des ports INPUT/OUTPUT.
- Sortie arrière Quarry vers un Deposit presque plein, avec conservation des objets.
- Face écran fermée du Deposit et reprise après rotation.
- Liaison HomeNetwork des quatre familles d'appareils, permissions, migration, propriétaire, détachement et sauvegarde Storage.

Les stocks initiaux de récoltes et minerai sont des fixtures. Les tests isolent
les transferts et ne couvrent pas une récolte ou un chantier complet. Dashboard et
Tasks sont chargés, mais ces cinq tests ne vérifient pas leurs scénarios métier.

Ce README décrit la configuration et la couverture ; il ne prouve pas qu'un test
a été exécuté. Les résultats de la validation historique HomeCore 1.10 sont dans
[PUBLICATION_1_10.md](../docs/PUBLICATION_1_10.md). Ils ne valident pas les versions
actuelles ni le nouveau UI Kit. Le [workflow CI](../docs/INTEGRATION_CI.md) charge
désormais les six consommateurs depuis sept SHA explicites ; son fichier seul ne
constitue pas une exécution réussie.

## English

This harness compiles real production sources for Dashboard 1.6.0, Storage 1.4.0,
Farm 1.5.0, Quarry 1.4.0, Energy 0.5.0 and Tasks 0.2.0, with HomeCore 1.14.0 from
the parent composite. All six consumers load in both the dedicated GameTest server
and the manual client run. Existing five transfer/permission GameTests are
unchanged; loading Dashboard/Tasks does not extend their business coverage.

Use the commands above with Java 21. Repository paths and exact expected versions
are configurable through each module's `*_dir` and `*_version` properties. Actual
Minecraft and HomeCore dependencies are validated rather than silently overwritten.
Optional viewer APIs are compile-only and are not added to the runtime.

`check` verifies compiled constant fields for complete duplicated shell palettes,
allows semantic colors and requires actual calls to the public HomeCore UI helpers
or buttons. `runClient` is a manual review entry point, not an automated smoke.
`runClientSmoke` automatically checks all seven production mods are loaded,
vanilla keyboard activation/tab order/text input and the actual shared frame
through a framebuffer pixel and a saved PNG. Its fixture is client-only and
does not create a world or exercise the individual business screens.
Native per-mod smokes and language/window/focus checks remain complementary.
Building the harness publishes nothing. Historical 1.10 results are not evidence
that the current versions or the UI migration passed validation.
