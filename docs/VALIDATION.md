# Validation des livraisons

## Publication et CI distante du 29 septembre 2026

Les correctifs ont été appliqués aux dépôts réels et poussés sur `main`.
Les CI des cinq mods, les publications Maven HomeCore 1.10.0 et Energy 0.2.2,
ainsi que les cinq GameTests intermods ont réussi sur GitHub Actions.
Les CI consommateurs utilisent les dépendances publiées sans Maven Local.
Les SHA, liens vers les exécutions et limites sont consignés dans le
[rapport de publication](PUBLICATION_1_10.md).

Le compte rendu local ci-dessous conserve les étapes et contraintes rencontrées
avant cette publication ; ses mentions d'opérations en attente décrivent cet
état antérieur.

## HomeCore 1.10.0 — validation locale du 29 septembre 2026

Java 21.0.11, Minecraft 1.21.1, NeoForge 21.1.250 pour HomeCore et 21.1.251 pour
les tests intermods. Le wrapper Gradle 8.8 a utilisé une copie du cache dans
`build/local-gradle`, le cache utilisateur étant alors en lecture seule.
Les sources des consommateurs ont été lues sans écrire dans leurs dépôts pendant
cette phase de validation. Ces restrictions d'écriture ont ensuite été levées ;
les résultats ci-dessous conservent les conditions des exécutions effectuées.

| Vérification exécutée | Résultat |
| --- | --- |
| `build test verifyReleaseJar` | Succès ; **122 tests JUnit**, aucun échec, erreur ou test ignoré |
| Javadoc avec avertissements bloquants | Succès après correction de la documentation des ports |
| `verifyDocumentation` | Versions du mod, de l'API, JAR et exemples Maven cohérentes dans les README FR/EN |
| `runPersistence -PpersistencePass=write` | **16 GameTests réussis** |
| `runPersistence -PpersistencePass=read` dans un second processus | **16 GameTests réussis**, mêmes identités persistantes |
| `publishMavenJavaPublicationToStagingRepository` | Publication locale réussie |
| `-p gradle/publication-smoke verifyPublishedArtifacts` | Résolution indépendante du JAR, sources, Javadoc, POM et métadonnées Gradle réussie |
| `-p integration-tests runGameTestServer` | **5 GameTests intermods réussis**, dernière exécution à 18:17:35, heure de Paris |

Le build et le staging ont aussi réussi avec le cache de configuration activé,
comme dans la CI. Le contrôle Maven utilise uniquement le dépôt de staging,
sans Maven Local ni substitution composite. Les archives résolues correspondent
aux fichiers produits dans `build/libs`.

Les deux nouveaux GameTests du connecteur vérifient la sélection des réseaux
administrables, la conservation des autres données de l'objet, la liaison via
provider, le propriétaire, la révocation des permissions source/destination et
la suppression d'un réseau.

Le serveur d'intégration charge les sources de production locales de HomeCore
1.10.0, Energy 0.2.2, Farm 1.2.0, Storage 1.1.0 et Quarry 1.2.0, depuis les
copies corrigées sous `build/release-validation`, avec leurs adaptations
ItemPort/NetworkMember. Les cinq scénarios vérifient :

- la production d'un vrai panneau solaire vers une station Farm, sans injection
  d'énergie, avec conservation des HE ;
- le transfert FarmBot Station vers un vrai Deposit et les restrictions INPUT/OUTPUT ;
- le transfert Quarry vers un vrai Deposit presque plein, sans perte ni duplication ;
- le refus d'une face fermée du Deposit puis la reprise après rotation ;
- les permissions source/destination, les propriétaires, le changement de réseau,
  le détachement et la sauvegarde/rechargement du Storage Controller.

Le premier essai solaire a échoué parce que le plafond du terrain GameTest
bloquait le ciel. La fixture dégage maintenant les quatre colonnes de l'emprise ;
les règles d'exposition et de production d'Energy n'ont pas été modifiées.

Les rapports JUnit sont dans `build/test-results/test`, le journal de persistance
dans `build/validation/persistence/logs` et le journal intermods dans
`integration-tests/build/server/logs`. Les métadonnées du harnais exigent
HomeCore 1.10.0 : elles ne remplacent pas la mise à jour des dépendances ni les
builds de livraison des consommateurs. Les validations natives ci-dessous sont
distinctes du harnais. Les clients graphiques et une session à plusieurs joueurs
n'ont pas été exécutés pendant cette migration.

Les copies de validation lisent les fichiers de travail des dépôts voisins puis
appliquent les correctifs préparés, sans modifier ces dépôts. Farm passe ses
40 tests JUnit, son contrôle JAR et 89 GameTests ; Quarry passe son build, son contrôle JAR et
47 GameTests ; Storage passe son build, son contrôle JAR et 10 GameTests dans
chacun des deux processus `write` puis `read`. Quarry et Storage n'ont pas de
sources JUnit : la tâche `test NO-SOURCE` ne compte pas comme des tests exécutés.

Energy passe ses 54 tests JUnit, son contrôle JAR et ses 61 GameTests après le
correctif de liaison. Sa publication de staging contient le binaire, les sources,
le POM et les métadonnées Gradle. Un projet consommateur indépendant a résolu ces
artefacts et la dépendance transitive exacte HomeCore 1.10.0 depuis les seuls
dépôts de staging. Il vérifie aussi que les archives résolues sont identiques aux
archives construites. Aucun Maven Local ni build composite dans ce contrôle.

Les correctifs CI des quatre consommateurs et la publication Energy sont prêts
dans `build/homelink-migration-patches`. Ils passent `git apply --check` contre
les dépôts voisins. La vérification JAR manquante de Farm y est ajoutée et a été
exécutée avec succès dans sa copie de validation. Les workflows distants restent
à exécuter après application et publication des dépendances.

Les GameTests natifs ont révélé un refus de liaison avant le premier tick dans
les wrappers consommateurs. La découverte du provider sert désormais de repli
si l'appareil n'est pas encore enregistré ; `DashboardAPI.bindDevice` conserve
tous les contrôles de permissions. HomeCore corrige aussi le cas d'un réseau
supprimé encore enregistré sur l'appareil : `UNKNOWN_NETWORK` remplace
`UNCHANGED`, avec un test vérifiant l'absence de mutation et le refus d'accès.

Au terme de cette validation locale, les workflows GitHub Actions sont préparés
mais non exécutés à distance. Aucun paquet Maven distant, commit ou push de cette
migration n'a encore été effectué. Les restrictions d'écriture Git et des dépôts
voisins sont levées : les correctifs validés peuvent maintenant être appliqués,
relus puis publiés. L'application des patches de versions, de dépendances et de
documentation, la migration des branches vers `main` et les publications distantes
restent des étapes distinctes des validations locales rapportées ici.

Les résultats ci-dessous sont ceux des livraisons antérieures.

Each phase is built and tested before the next phase starts. Gradle uses JDK 21,
Minecraft 1.21.1 and NeoForge 21.1.250. Compilation fails on deprecated, removal
and unchecked API warnings. No consumer mod is required.

## Phase 1

`clean build test`: successful (`test NO-SOURCE`). Development client started,
recognized HomeCore, reached its welcome screen and exited successfully.
Configured Gradle wrapper/build properties and NeoForge metadata; replaced the
initial `CoreLink.homecore` scaffold with `fr.lkdm.homecore.HomeCore`, the logger,
`DashboardAPI.API_VERSION` and package boundaries. No consumer dependencies.

## Phase 2

`build test`: successful; 13 JUnit tests, zero failures/errors. Covers metric
ranges, invalid values, action validation, schema and a logical debug machine.
Created `api.device`, `api.metric`, `api.action` and their tests; configured JUnit.
Devices are logical objects. Metadata is immutable, metric revisions are atomic,
and actions remain trusted server definitions. Registry and networking deferred.

## Phase 3

`build test`: successful; 21 JUnit tests, zero failures/errors. Created public
registries/providers, a server-scoped runtime and provider/registry tests.
Duplicate UUIDs are rejected, invalid entries are pruned lazily, and providers
adapt only explicitly supplied block entities. No global world scan. Runtime state
is cleared on server stop. POSITION actions now snapshot mutable positions before
validation. Events and capabilities deferred to phase 4.

## Phase 4

`build test`: successful; 31 JUnit tests, zero failures/errors. Created public event
and capability APIs, wired the per-server bus and added lifecycle/integration tests.
Events deliver synchronously on the caller thread, isolate failed listeners and
release callbacks on unsubscribe/close. Capabilities have typed immutable bindings;
no energy/inventory/farm/security subsystem is implemented. Home networks deferred.

## Phase 5

`build test`: successful; 39 JUnit tests, zero failures/errors. Created `api.network`,
`HomeNetworkSavedData`, persistence tests and an isolated verification source set.
Networks store persistent UUID membership rather than retaining loaded devices.
All mutations mark Overworld SavedData dirty; corrupt or unsupported data fails
closed instead of being replaced with empty storage.

Real process restart verification also passed:

1. `runPersistence -PpersistencePass=write`: creates and saves network/member/device.
2. `runPersistence -PpersistencePass=read`: a new Minecraft GameTestServer loads
   the same world and asserts the same network UUID, owner, member and device.

Observed UUID in both processes: `aba1b13d-b867-4ca8-8280-e38f035225af`.
The first fresh server run emitted Minecraft's missing `server.properties` message,
then created the file and passed; the second run passed without that message.
No EULA file was edited (NeoForge's GameTestServer has its own test exemption).
Permissions and request protection are deferred to phase 6.

## Phase 6

`build test`: successful; 50 JUnit tests, zero failures/errors. Created public
permissions, immutable role policy, action executor and bounded token-bucket limiter.
The authenticated-player facade enforces the server thread. Current network/device
membership, CONTROL and action-specific permission are required on every call.
Unknown permissions fail closed. Every attempt consumes a shared player budget;
disconnecting does not reset it. Viewer denial, owner/member grants, revocation,
offline/missing devices, invalid values and spam were exercised. Networking deferred.

## Phase 7

`build test`: successful; 70 JUnit tests, zero failures/errors. Created bounded
payload codecs, authorized server subscriptions, client cache and client facade.
Only subscribed pages are checked; changed metrics use revision deltas, current
permissions are revalidated and all world interaction runs on the server thread.
Codecs reject oversized/invalid data. Transport has no dependency on other mods.

`runNetworkSmoke`: successful, including clean world save and client shutdown.
The real connected client received its initial metrics, requested an action,
received SUCCESS and a metric delta (85.0), without a second full snapshot:
`HOMECORE_NETWORK_SMOKE_OK snapshots=1 deltas=1` and
`HOMECORE_NETWORK_SMOKE_SHUTDOWN_OK`. The first fixture shutdown was corrected to
disconnect the level before waiting for the integrated server; the rerun exited 0.
SDK examples, development-only debug device and release documentation deferred to phase 8.

## Phase 8

`clean build test javadoc --warning-mode all`: successful on JDK 21; 75 JUnit tests,
zero failures/errors. Java compilation treats deprecated/removal/unchecked API
warnings as errors. Public API JavaDoc uses full doclint (including missing
documentation) and treats warnings as errors. Sources, public API JavaDoc and
release JARs are produced at version 1.0.0; API version remains 1.0.0.

`runDebugSmoke`: successful with HomeCore 1.0.0. A real connected client executed
`/homecore_debug`, discovered the device, received its metrics, requested
`set_progress`, received SUCCESS and Percentage(85), and received
`homecore:test_event`. There was exactly one full snapshot and one metric delta.
The integrated server saved and the client exited normally. The standalone
example's five initial values, all three actions, events, schema and capability
are also covered by JUnit tests.

Final release persistence rerun also passed in two separate GameTestServer
processes after the clean build: `runPersistence -PpersistencePass=write`, then
`runPersistence -PpersistencePass=read`. Both reported network UUID
`2953e021-a717-47e3-8f17-f553dfbfa26c`; owner, member role and device membership
survived the restart. Both Gradle tasks exited 0 with all required GameTests
passing and no ERROR/exception entries. This also verified dedicated-server
loading after the client/server transport was added.

Artifacts: `build/libs/homecore-1.0.0.jar` (150195 bytes),
`homecore-1.0.0-sources.jar` and `homecore-1.0.0-javadoc.jar`.
Release JAR SHA-256:
`820d596923dce6cbb4f83724ffb98ceeb91a671aa7efbc8f97fe4ca70d7957f1`.

Created `src/examples/.../ExampleMachineDevice.java`, the isolated
`src/development` debug mod, example behavior tests, `docs/PROTOCOL.md` and
`LICENSE`. Expanded `README.md` into the developer SDK guide and documented every
public API package. Updated Gradle to compile examples/development/verification
sources separately, generate JavaDoc and verify release contents. Updated
`NetworkSmoke` for development command/event verification and `gradle.properties`
for release 1.0.0. `.gitignore` excludes the new development working directories.

Final review also aligned advertised action text limits with the wire limit and
rejected oversized item/fluid identifiers before transmission, with regression
tests. `verifyReleaseJar` passes and is part of `check`: development, example,
verification and JUnit classes are absent; mod metadata and license are present.

### Architecture and review conclusions

- Consumer mods use `api.*`; HomeCore imports no consumer implementation.
- Logical devices do not require BlockEntities. Providers adapt explicit objects;
  no world scanner or global tick traversal was introduced.
- Server-owned state is accessed on the server thread. Metrics have synchronized
  atomic value/revision snapshots; mutable Components, positions and NBT crossing
  API boundaries are copied where required.
- Network membership is persisted as UUIDs. Live Java device objects are
  registered by their integrations after loading; failed/corrupt persistence is
  not silently replaced with empty data.
- Remote actions derive identity from the authenticated connection and share a
  server-side per-player rate budget. Current permissions, membership, status and
  values are checked before handlers run.
- Wire codecs bound lengths/counts and use closed value types; no Java class name
  or arbitrary executable object is deserialized. Enum choices resolve against
  declared options. Packet handlers return to the appropriate game thread.
- Synchronization checks only active pages and sends changed metric revisions.
  Caches, subscriptions, request budgets and event delivery have documented limits.
  Event subscriptions, client listeners and server state have explicit teardown.
- The debug mod has a separate source set and a production guard. The build checks
  its exclusion from release artifacts rather than relying only on the guard.
- Runtime logs contain Minecraft/NeoForge development warnings (command ambiguity,
  resource URL scheme, vanilla sound/shader warnings and initial world-load tick
  delay); successful final smoke runs contain no HomeCore exception.

No Dashboard UI, radio-range simulation, full inventory/energy subsystem or
consumer mod implementation is included; these were outside the requested core.
No load benchmark or compatibility test with third-party mods is claimed.
# Validation complémentaire : transport réseau 1.1.0

`./gradlew.bat build test` a réussi après l'ajout du protocole 2 et de l'abonnement
réseau borné. Les nouveaux tests couvrent 100 appareils actifs, 400 changements de
Metrics, les budgets de snapshots/deltas, 128 appareils produisant chacun 32 Metrics
en continu sans famine, la suppression et l'ajout d'appareils, l'isolation d'un
provider défaillant et sa récupération, la révocation des permissions, les événements
au-delà des 128 appareils suivis, la conservation des deltas en cache et les codecs.
Le contrôle d'archive vérifie désormais la version du projet et a validé le JAR
`homecore-1.1.0.jar`. Les résultats 1.0.0 ci-dessous sont conservés comme historique.

## Version 1.3.0 — liaison et noms

Build complet réussi le 19 septembre 2026 : 89 tests unitaires. Les nouveaux tests couvrent les contraintes de liaison côté actions et synchronisation, le retour des appareils joignables, le renommage sans changement d'identité et la conservation du nouveau nom après sauvegarde disque et rechargement successif. Journal local : build/network-names-validation.log.

## Version 1.5.0 — Electronics Workbench

Validation du 25 septembre 2026 sur Minecraft 1.21.1, NeoForge 21.1.250 et Java 21 :

- `build` : 100 tests unitaires réussis ; contrôle du JAR, modèles, textures,
  traductions et exclusion des fixtures de développement.
- `runPersistence -PpersistencePass=write` puis `read` : huit GameTests réussis
  dans chaque processus. Les contrôles couvrent les deux recettes, les lots,
  les quantités invalides, les sorties pleines, la propriété des sessions,
  les étapes rejouées, les annulations, la fermeture et l'absence du joueur,
  la destruction du bloc et les récipients restitués après fabrication.
- Une production sauvegardée dans un chunk distant reprend après arrêt et
  redémarrage effectifs du serveur : 16 circuits exactement, conservation des
  autres objets stockés, aucune seconde production après achèvement.
- Le client de vérification fabrique 64 circuits puis 64 microprocesseurs,
  en alternant glisser-déposer et clic-clic avec accusés serveur. Il contrôle
  également la recette vanilla de l'établi, son déblocage, le transfert rapide
  vers les matériaux et la récupération des lots.
- Les quatre états du bloc, dans leurs quatre orientations, sont contrôlés
  après chargement réel des modèles et textures par Minecraft. Les modèles des
  deux composants sont également vérifiés contre les textures manquantes.
- Interfaces anglaise et française contrôlées dans Minecraft avec
  `runWorkbenchSmoke` et `runWorkbenchSmoke -PworkbenchLanguage=fr_fr` réussis ;
  captures des phases sélection,
  assemblage, production et résultat dans le dossier local
  `build/validation/workbench-client/screenshots/fr_fr/`.

Les marqueurs `HOMECORE_WORKBENCH_SCREEN_OK`, `HOMECORE_WORKBENCH_MODELS_OK`
et `HOMECORE_NETWORK_SMOKE_OK` sont exigés par la tâche client. Le transport
Device/Metric/Action existant reste couvert par le même client connecté.
Les tests ne constituent pas un benchmark ni une validation avec des modpacks tiers.

## Version 1.6.0 — ergonomie et établi sur deux blocs

La validation du 25 septembre 2026 couvre la grille de matériaux latérale,
les onglets de recettes, les quantités finales, MAX et la palette d'assemblage.
Le build et ses 101 tests unitaires réussissent ; le JAR contient les nouveaux
modèles et exclut les fixtures de développement.
Le client vérifie qu'un lot de 64 est refusé quand les ressources n'en permettent
que 32, que MAX choisit 32, puis que les deux composants se fabriquent par lots
de 64 après réapprovisionnement.

Douze GameTests passent dans chacune des deux exécutions `runPersistence`
(`write`, puis `read`). Les nouveaux cas vérifient les quatre orientations,
le refus de pose sur un bloc ou une entité, l'inventaire commun, la casse de
chaque moitié en survie et en créatif, ainsi que les explosions. Entrées,
réservations et résultat sont comptés après destruction. Un ancien état NBT
sans propriété `part` reste un établi compact utilisable, et un établi voisin
indépendant reste intact.

Le redémarrage réel du serveur restaure les deux moitiés avec une seule
BlockEntity, puis termine la production une seule fois. Le client pose l'établi
avec son BlockItem et ouvre l'interface depuis la moitié droite. Les 48 variantes
de bloc et le modèle d'item complet sont contrôlés après chargement par Minecraft.
Les captures locales incluent `workbench-world.png`, l'interface, le manque de
matériaux, MAX et les étapes des deux recettes.
Les parcours clients anglais et français réussissent ; le dernier build vérifie
également le modèle corrigé pour éviter le chevauchement des faces de la bordure.

## Version 1.6.1 — harmonie visuelle avec HomeLink Farm

La palette, les panneaux encastrés et les boutons biseautés suivent HomeLink Farm
1.0.0 (révision `87f744d`). Les deux textures d'acier sont intégrées sans modification,
avec attribution dans `META-INF/NOTICE`. Les dimensions des quatre modèles et les
interactions restent identiques ; aucune dépendance envers Farm n'est ajoutée.

`build runWorkbenchSmoke -PworkbenchLanguage=fr_fr` puis `runWorkbenchSmoke`
réussissent le 25 septembre 2026. Les 101 tests unitaires passent sans erreur.
Les clients français et anglais fabriquent chacun les deux lots de 64, vérifient
MAX, les ressources insuffisantes, le glisser-déposer, le clic-clic et la sortie.
Les 48 variantes et le modèle d'item passent le chargement réel de Minecraft.
Les captures du modèle en jeu et de l'interface ont été examinées. Le contrôle
du JAR vérifie également les textures d'acier et leur attribution.

## Version 1.7.0 — modules de communication et de contrôle

Deux composants s'ajoutent au circuit imprimé et au microprocesseur :
`homecore:homelink_communication_module` et `homecore:homelink_control_module`.
Ils se fabriquent uniquement à l'établi électronique, par le type de recette
existant `homecore:electronics`, avec chacun un plan d'assemblage propre.
Les onglets de l'établi suivent l'ordre d'enregistrement des objets produits.

Le 26 septembre 2026, `build` réussit : 101 tests unitaires passent et le contrôle
du JAR exige les modèles, textures et recettes des deux modules.
Quatorze GameTests passent dans chacune des exécutions `runPersistence`
(`write`, puis `read`). Les nouveaux cas vérifient l'enregistrement, la pile de 64,
la rareté commune, l'infobulle traduite, l'ordre de l'onglet créatif, l'absence de
recette vanilla et les ingrédients exacts. Ils couvrent aussi les lots de 1, 8, 32
et 64, un placement erroné, l'annulation, la fermeture, l'absence du joueur, le
rechargement, une sortie étrangère ou pleine, sans perte ni duplication.

`runWorkbenchSmoke` réussit en anglais et en français. Le client fabrique quatre
lots de 64 (circuit, microprocesseur, module de communication, module de
contrôle) par glisser-déposer et clic-clic. Les modèles 3D des quatre composants
passent le chargement réel de Minecraft. Les captures des deux nouveaux plans
d'assemblage ont été examinées.

## Version 1.12.0 — affichage dans JEI et REI

Les recettes `homecore:electronics` disposent d'une catégorie dédiée dans JEI
(API 19.57.0.449) et REI (API 16.0.799). Les deux intégrations sont optionnelles,
côté client, compilées contre l'API seule et absentes des dépendances requises.

Le 29 septembre 2026, `build` réussit : 126 tests unitaires passent et le contrôle
du JAR exige les deux plugins. `runWorkbenchSmoke` réussit sans visualiseur de
recettes, avec `-PwithJei` puis avec `-PwithRei`. Avec JEI, les quatre recettes
de l'établi sont enregistrées dans la catégorie. Avec REI, le plugin est chargé
et les quatre recettes produisent chacune un affichage. Aucune erreur n'apparaît
dans les journaux client. Le rendu de la catégorie n'a pas été examiné par capture.
