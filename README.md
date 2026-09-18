# HomeCore 1.0.0

API commune pour les appareils et réseaux d'une base Minecraft. HomeCore ne dépend d'aucun mod consommateur : Home Dashboard, Farm Monitor et une carte holographique peuvent utiliser ses contrats sans que HomeCore connaisse leurs implémentations.

Minecraft **1.21.1**, NeoForge **21.1.250**, Java **21**. API : `DashboardAPI.API_VERSION = "1.0.0"`.

## Construire et installer

Configurer `JAVA_HOME` vers un JDK 21 puis utiliser le wrapper :

```powershell
./gradlew.bat clean build test javadoc
./gradlew.bat runClient
```

Sur Linux/macOS, utiliser `./gradlew`. Les JAR sont dans `build/libs` ; installer `homecore-1.0.0.jar` dans `mods` côté client et serveur. Le serveur de jeu normal requiert l'acceptation de l'EULA Minecraft par son administrateur.

Pour développer un mod consommateur, publier d'abord localement HomeCore avec `./gradlew.bat publishToMavenLocal`, puis ajouter dans son projet ModDevGradle :

```groovy
repositories { mavenLocal() }
dependencies { implementation 'fr.lkdm.homecore:homecore:1.0.0' }
```

Ajouter à son `neoforge.mods.toml`, en remplaçant `examplemod` par son identifiant :

```toml
[[dependencies.examplemod]]
modId="homecore"
type="required"
versionRange="[1.0.0,2.0.0)"
ordering="AFTER"
side="BOTH"
```

Aucun dépôt distant de distribution n'est présumé. La publication locale est une commande à exécuter explicitement, pas une étape de `build`.

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
```

Le smoke de développement vérifie aussi la commande, l'action `set_progress` et la notification de l'événement. Les cinq valeurs initiales et les trois actions sont couvertes par les tests JUnit. Les passes de persistance utilisent le même monde isolé. Le smoke client crée un monde intégré isolé, vérifie découverte, snapshot, action, résultat et delta sans snapshot supplémentaire, puis ferme le client. Les validations effectivement réalisées sont documentées dans [VALIDATION.md](docs/VALIDATION.md).
