# HomeCore 1.9.0

English version: [README.en.md](README.en.md).

CurseForge description (English): [CURSEFORGE_DESCRIPTION.md](CURSEFORGE_DESCRIPTION.md).

CurseForge publishing setup: [English README](README.en.md#publish-to-curseforge).

API commune pour les appareils et réseaux d'une base Minecraft. HomeCore ne dépend d'aucun mod consommateur : Home Dashboard, Farm Monitor et une carte holographique peuvent utiliser ses contrats sans que HomeCore connaisse leurs implémentations.

Minecraft **1.21.1**, NeoForge **21.1.250**, Java **21**. API : `DashboardAPI.API_VERSION = "1.3.0"`.

## Construire et installer

Configurer `JAVA_HOME` vers un JDK 21 puis utiliser le wrapper :

```powershell
./gradlew.bat clean build test javadoc
./gradlew.bat runClient
```

Sur Linux/macOS, utiliser `./gradlew`. Les JAR sont dans `build/libs` ; installer `homecore-1.7.0.jar` dans `mods` côté client et serveur. Le serveur de jeu normal requiert l'acceptation de l'EULA Minecraft par son administrateur.

Pour développer un mod consommateur, publier d'abord localement HomeCore avec `./gradlew.bat publishToMavenLocal`, puis ajouter dans son projet ModDevGradle :

```groovy
repositories { mavenLocal() }
dependencies { implementation 'fr.lkdm.homecore:homecore:1.7.0' }
```

Ajouter à son `neoforge.mods.toml`, en remplaçant `examplemod` par son identifiant :

```toml
[[dependencies.examplemod]]
modId="homecore"
type="required"
versionRange="[1.7.0,2.0.0)"
ordering="AFTER"
side="BOTH"
```

Aucun dépôt distant de distribution n'est présumé. La publication locale est une commande à exécuter explicitement, pas une étape de `build`.

## HomeLink Components

### HomeLink Circuit Board

Basic electronic component used by HomeLink-compatible devices. Assembled exclusively in the Electronics Workbench from vanilla Copper, Redstone and Quartz. Each assembly yields two boards.

### HomeLink Microprocessor

Advanced processing component used by more complex HomeLink devices. Assembled exclusively in the Electronics Workbench using a HomeLink Circuit Board and vanilla Gold Nuggets, Copper, Redstone and Quartz.

### HomeLink Communication Module

Communication, networking and sensing component for HomeLink devices that transmit data or detect signals. Assembled exclusively in the Electronics Workbench from a HomeLink Circuit Board, a HomeLink Microprocessor and vanilla Copper, Redstone, Quartz and an Amethyst Shard.

### HomeLink Control Module

Machine control and automation component for HomeLink devices that act on the world: motors, mechanisms and automated machines. Assembled exclusively in the Electronics Workbench from a HomeLink Circuit Board, a HomeLink Microprocessor, a Comparator and vanilla Copper, Redstone and Iron.

### HomeLink Electronics Components

| Component | ID | Role |
| --- | --- | --- |
| Circuit Board | `homecore:homelink_circuit_board` | Basic electronics. |
| Microprocessor | `homecore:homelink_microprocessor` | Processing and logic. |
| Communication Module | `homecore:homelink_communication_module` | Communication, networking and sensing. |
| Control Module | `homecore:homelink_control_module` | Machine control and automation. |

All four are made in the Electronics Workbench according to their current recipes, and none has a vanilla crafting-table recipe. The two modules are specialised components, not higher tiers of the board or processor. These IDs are stable: consumer mods can reference them directly without depending on each other. HomeCore itself does not depend on any consumer mod.

## HomeLink Electronics Workbench

`homecore:electronics_workbench` is the shared assembly station for HomeLink electronics. It requires no energy or fuel. Craft it in a vanilla crafting table using this pattern:

```text
I R I    I = Iron Ingot       R = Redstone
C W C    C = Copper Ingot     W = Crafting Table
P P P    P = Any Planks
```

The workbench occupies two adjacent blocks, with a continuous countertop and one shared inventory. Leave both spaces clear when placing it. Either half opens the same interface; breaking either half recovers one workbench and its contents in survival. Existing single-block workbenches remain usable with their saved contents; break and replace them to adopt the wider shape.

Its steel frame, recessed interface panels and warm metallic accents follow HomeLink Farm's visual style. The shared steel textures are bundled with HomeCore; HomeLink Farm is not required.

Place materials in the nine input slots grouped on the left, select a component directly from its icon tab and choose the number of **finished items** (up to 64). Quantities follow the recipe yield: circuit boards advance in pairs; microprocessors and modules advance individually. Quick quantities and a MAX button simplify batch selection. Ingredient icons show available and required counts before assembly. Output storage stays visible alongside the material grid.

Assemble one prototype per batch, using drag and drop or clicking a part and then its matching socket. Correct placements snap into place; mistakes cost no materials. A short production animation follows validation, then the completed batch appears in the output slot.

The server reserves ingredients before the prototype begins and validates every placement. Cancelling, closing the prototype screen or disconnecting before validation returns reserved materials to the input slots. Interrupted prototypes are also refunded after a world reload. Validated production continues after the screen closes and resumes when the workbench loads again. During a reserved batch its inventory is locked. Breaking the workbench drops stored items, finished output and the reserved ingredients of any unfinished batch. None of the four components has a vanilla crafting-table recipe.

Other HomeLink mods can add recipes through the `homecore:electronics` recipe type in their own data resources; they do not need to depend on each other. Existing Device, Metric, Action, Event and HomeNetwork contracts remain unchanged.

See [Electronics recipe format](docs/ELECTRONICS.md) for the extensible JSON schema.

## Contrats et cycle de vie

Les contrats publics se trouvent dans `fr.lkdm.homecore.api.*`. La JavaDoc est générée dans `build/docs/javadoc/index.html`. Un consommateur n'a pas besoin d'importer les packages d'implémentation hors `api`.

Un `DashboardDevice` est un appareil logique : BlockEntity, système de jeu ou machine sans position. Son UUID doit rester identique après sauvegarde/rechargement. Son type, ses métriques et ses actions restent stables tant qu'il est enregistré ; les valeurs peuvent changer.

Implémentation minimale, avec les types importés depuis l'API :

```java
public final class ExampleDevice implements DashboardDevice {
    private final UUID persistentId;
    public ExampleDevice(UUID persistentId) { this.persistentId = persistentId; }
    @Override public UUID id() { return persistentId; }
    @Override public ResourceLocation deviceType() {
        return ResourceLocation.fromNamespaceAndPath("examplemod", "machine");
    }
    @Override public Component displayName() { return Component.literal("Example machine"); }
    @Override public DeviceStatus status() { return DeviceStatus.ONLINE; }
}
```

Sur le thread serveur, appeler `DashboardAPI.devices(server).register(device)`, puis `unregister(device.id())` lors du retrait ou déchargement de la source. Le registre ne balaie pas les chunks. `isValid()` écarte aussi un objet retiré lors d'une consultation. Les UUID dupliqués sont refusés.

L'[exemple compilable complet](src/examples/java/fr/lkdm/homecore/example/ExampleMachineDevice.java) montre métriques, actions, événements, provider, schéma et capability. Il est séparé du JAR distribué. Son état mémoire est une démonstration : une vraie machine sauvegarde son identité et ses valeurs dans sa propre persistance.

## Métriques et schéma

```java
private final DeviceMetric<Double> temperature = DeviceMetric.builder(
        ResourceLocation.fromNamespaceAndPath("examplemod", "temperature"),
        Component.literal("Temperature"), MetricTypes.DOUBLE, 22.5)
    .unit(fr.lkdm.homecore.api.metric.Unit.CELSIUS)
    .updatePolicy(UpdatePolicy.NORMAL)
    .build();

@Override public List<DeviceMetric<?>> metrics() { return List.of(temperature); }
```

`temperature.setValue(23.0)` valide et incrémente la révision uniquement si la valeur change. `MetricRange` utilise des bornes inclusives et un pas exact ; zéro signifie continu. Les types couvrent booléens, entiers, longs, doubles finis, texte, énumérations, pourcentage, durée, position, item, fluide et énergie. Les items/fluides transportent un identifiant et une quantité, sans inventaire complet.

Les unités sont extensibles avec `new Unit(id, symbol)`. Un `MetricType` personnalisé est utilisable localement ; sa valeur doit appartenir aux représentations fermées de `WireValue` pour être exposée sur le réseau.

`device.schema()` décrit métriques, actions et événements pour construire une interface. Conserver leurs instances ; ne pas les recréer à chaque appel. REALTIME et ON_CHANGE sont examinés chaque tick, FAST tous les 5 ticks, NORMAL tous les 20, SLOW tous les 100. Seules les révisions modifiées sont envoyées. STATIC apparaît uniquement dans les snapshots.

## Actions et permissions

Avec `progress` une métrique double exposée par l'appareil :

```java
private final DeviceAction<Double> setProgress = DeviceAction.slider(
        ResourceLocation.fromNamespaceAndPath("examplemod", "set_progress"),
        Component.literal("Set progress"), 0, 100)
    .step(1)
    .requiredPermission(Permission.CONTROL.id())
    .handler((context, value) -> {
        progress.setValue(value);
        return ActionResult.success();
    }).build();
```

Exposer cette instance via `actions()`. Type, bornes, pas, options SELECT et prédicat supplémentaire sont vérifiés avant le handler. Les boutons utilisent `fr.lkdm.homecore.api.action.Unit.INSTANCE`. Les validateurs doivent être purs. Les handlers s'exécutent sur le thread appelant.

Pour une demande d'un joueur, appeler sur le thread serveur :

```java
ActionResult result = DashboardAPI.executeAction(
    serverPlayer, networkId, deviceId, actionId, 73.0);
```

L'identité vient du `ServerPlayer` authentifié. Ne pas exposer directement `DeviceAction.execute` aux requêtes distantes : cette méthode valide le paramètre mais n'accorde aucune autorisation.

La passerelle vérifie existence, appartenance au réseau, CONTROL, permission spécifique, état ONLINE et paramètre. Une permission inconnue est refusée. La limite commune par joueur autorise une rafale de 10 demandes rechargée à 10/s. Les demandes refusées/invalides consomment aussi ce budget ; une reconnexion ne le réinitialise pas.

| Rôle | Permissions par défaut |
| --- | --- |
| OWNER | Toutes les permissions reconnues |
| ADMIN | VIEW, CONTROL, AUTOMATE, CONFIGURE, MANAGE_NETWORK |
| MEMBER | VIEW, CONTROL, AUTOMATE |
| VIEWER | VIEW |

`DashboardAPI.hasPermission` vérifie un droit courant. La mutation directe d'un réseau par Java est une API serveur de confiance : un mod qui l'expose aux joueurs doit vérifier MANAGE_NETWORK lui-même. Un `PermissionValidator` personnalisé s'utilise dans une passerelle construite par le consommateur ; il ne remplace pas automatiquement la politique du transport HomeCore.

## Réseaux persistants

`DashboardAPI.networks(server)` expose `createNetwork`, `deleteNetwork`, `getNetwork`, `getNetworksForPlayer`, `addDevice`, `removeDevice`, `getDevices`, `setMember` et `removeMember` sur le thread serveur. Les snapshots sont immuables. Le propriétaire ne peut pas être retiré ou rétrogradé par ces méthodes.

Les réseaux utilisent les SavedData de l'Overworld, partagées entre dimensions. Leurs UUID d'appareils survivent au redémarrage ; les intégrations réinscrivent les objets Java au chargement. CONNECTED, UNREACHABLE et OFFLINE sont disponibles pour les consommateurs ; aucun calcul radio de portée n'est fourni.

## Événements

Déclarer les identifiants dans `DashboardDevice.eventTypes()`, puis publier sur le thread serveur :

```java
DashboardAPI.events(server).publish(new DeviceEvent(
    eventId, device.id(), Instant.now(), DeviceEvent.Severity.INFO,
    Map.of("state", "completed")));
```

`subscribe(listener)` accepte plusieurs auditeurs et retourne une subscription `AutoCloseable`. La fermer lors du démontage du consommateur pour libérer ses références. Le bus est fermé à l'arrêt du serveur. Les données structurées sont des champs texte nommés, sans objets exécutables. Le réseau transmet l'événement aux abonnés autorisés à voir son appareil.

## Provider de BlockEntity

Pendant l'initialisation commune du mod consommateur :

```java
DashboardAPI.registerDeviceProvider(MY_BLOCK_ENTITY_TYPE.get(),
    blockEntity -> new MyMachineDevice(blockEntity));
```

`MyMachineDevice` implémente `DashboardDevice`. Au chargement serveur de la BlockEntity :

```java
DashboardAPI.providers().discover(blockEntity)
    .ifPresent(device -> DashboardAPI.devices(server).register(device));
```

Éviter les inscriptions répétées et retirer l'appareil au déchargement. Le provider n'installe aucun scanner ; `discover` n'enregistre pas automatiquement son résultat. La surcharge avec `Class<T>` contrôle explicitement le type d'exécution ; l'autre repose sur l'association générique de `BlockEntityType<T>`.

## Capabilities

Définir un contrat Java et l'inscrire une fois via `DashboardAPI.capabilities().register(new DeviceCapability<>(id, MyCapability.class))`. Conserver le `DeviceCapability<MyCapability>` retourné. Dans l'appareil, construire `CapabilitySet.builder().add(descriptor, implementation).build()`, retourner ses `ids()` dans `capabilities()` et déléguer `capability(descriptor)` à `query`.

`device.capability(descriptor)` retourne un `Optional` typé. L'implémentation reste locale au serveur ; seuls les identifiants sont transportés. HomeCore n'impose aucun système énergie/inventaire complet.

## Client Dashboard

Pour un Dashboard, utiliser `HomeCoreClient.subscribeNetwork(networkId)` : abonnement
actif jusqu'à 128 appareils, snapshots initiaux répartis sur plusieurs ticks puis
deltas uniquement. `NetworkWatchResponse.truncated()` signale explicitement les
réseaux dépassant cette limite. Les changements de liste et de permissions sont
transmis sans recharger les appareils conservés ; les événements couvrent tout le
réseau autorisé. HomeCore 1.7.0 utilise le protocole réseau 2, requis sur les deux
côtés. L'ancienne API paginée reste disponible.

Depuis du code client uniquement, `HomeCoreClient.requestDevices(Optional.empty(), 0)` demande les réseaux visibles. Passer ensuite `Optional.of(networkId)` pour recevoir appareils, snapshots et changements. Une page contient au maximum 16 identifiants ; utiliser `nextOffset` pour continuer. Une seule page d'appareils est active par joueur.

`HomeCoreClient.executeAction(networkId, deviceId, actionId, parameter)` retourne l'UUID de corrélation. `ClientDeviceCache.INSTANCE.listen(...)` observe les réponses, résultats et deltas. Fermer l'auditeur et appeler `unsubscribe(networkId)` quand le Dashboard n'en a plus besoin. La déconnexion vide état et auditeurs.

Le cache conserve le snapshot initial dans `devices()` et les dernières modifications dans `metricUpdates()` : appliquer les deltas par clé/révision pour afficher l'état courant. Il ne réécrit pas le NBT initial à chaque changement. Voir le [contrat de transport](docs/PROTOCOL.md).

## Développement et vérification

Lancer `./gradlew.bat runDebugClient`, puis utiliser `/homecore_debug` pour installer l'appareil de développement. Il expose température, énergie, activation, progression et compteur, les actions `toggle_enabled`, `set_progress`, `reset_counter` et l'événement `homecore:test_event`. Le mod de développement, les exemples et les fixtures sont exclus du JAR release principal.

```powershell
./gradlew.bat test
./gradlew.bat runPersistence -PpersistencePass=write
./gradlew.bat runPersistence -PpersistencePass=read
./gradlew.bat runNetworkSmoke
./gradlew.bat runDebugSmoke
./gradlew.bat runWorkbenchSmoke
./gradlew.bat runWorkbenchSmoke -PworkbenchLanguage=fr_fr
```

Le smoke de développement vérifie aussi la commande, l'action `set_progress` et la notification de l'événement. Les cinq valeurs initiales et les trois actions sont couvertes par les tests JUnit. Les passes de persistance utilisent le même monde isolé. Le smoke client crée un monde intégré isolé, vérifie découverte, snapshot, action, résultat et delta sans snapshot supplémentaire, puis ferme le client. Les validations effectivement réalisées sont documentées dans [VALIDATION.md](docs/VALIDATION.md).

## Contraintes de liaison

HomeNetworkManager.setReachabilityPolicy(id, predicate) installe une contrainte de liaison éphémère, à réinstaller au démarrage du serveur. Chaque politique doit accepter le réseau et l'appareil. isReachable vérifie l'appartenance et ces contraintes ; une exception refuse la liaison. Les listes, snapshots, deltas, événements et actions utilisent cette décision, en plus des permissions. HomeCore ne calcule pas de portée : cette logique appartient au consommateur. L'absence de politique conserve le comportement logique existant. Le protocole réseau reste 2.

## Renommage

HomeNetworkManager.renameNetwork(id, name) conserve l'UUID, les membres, les appareils et la date de création. Les snapshots de réseau et la sauvegarde reflètent le nouveau nom. Cette API est réservée au code serveur de confiance : un appel provenant d'un joueur doit vérifier MANAGE_NETWORK avant la mutation. Les noms doivent contenir 1 à 128 caractères et ne pas être vides.

## Rattachement des appareils

Un `DashboardDevice` dont le bloc mémorise son réseau peut implémenter `NetworkMember` : réseau enregistré, propriétaire, droit propre à la machine (`canConfigure`) et notification `homeNetworkChanged`. Un Dashboard peut alors lister le rattachement d'une machine et la déplacer sans désynchroniser son bloc.

Pour une demande de joueur, appeler `DashboardAPI.bindDevice(player, device, Optional.of(networkId))`, ou `Optional.empty()` pour détacher. HomeCore exige `canConfigure` et MANAGE_NETWORK sur le réseau de destination et sur le réseau précédent. L'appareil rejoint la destination avant de quitter l'ancien réseau ; rien ne change si une vérification échoue. Un appareil sans `NetworkMember` renvoie `NOT_SUPPORTED`. API 1.5.0.
