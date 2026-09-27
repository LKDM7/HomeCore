# HomeCore 1.9.0

French version: [README.md](README.md).

HomeCore is a shared API for devices and networks in a Minecraft base. It does not depend on consumer mods: Home Dashboard, Farm Monitor and a holographic map can use its contracts without HomeCore knowing about their implementations.

Minecraft **1.21.1**, NeoForge **21.1.250**, Java **21**. API: `DashboardAPI.API_VERSION = "1.3.0"`.

## Build and install

Set `JAVA_HOME` to a JDK 21 installation, then use the Gradle wrapper:

```powershell
./gradlew.bat clean build test javadoc
./gradlew.bat runClient
```

On Linux/macOS, use `./gradlew`. JAR files are written to `build/libs`; install `homecore-1.7.0.jar` in the `mods` folder on both the client and server. A normal Minecraft server requires its administrator to accept the Minecraft EULA.

To develop a consumer mod, first publish HomeCore to your local Maven repository with `./gradlew.bat publishToMavenLocal`, then add this to the ModDevGradle project:

```groovy
repositories { mavenLocal() }
dependencies { implementation 'fr.lkdm.homecore:homecore:1.7.0' }
```

Add this to its `neoforge.mods.toml`, replacing `examplemod` with its mod ID:

```toml
[[dependencies.examplemod]]
modId="homecore"
type="required"
versionRange="[1.7.0,2.0.0)"
ordering="AFTER"
side="BOTH"
```

No remote distribution repository is assumed. Publishing to Maven Local is an explicit command and is not part of `build`.

## Publish to CurseForge

The `publishHomeCoreToCurseForge` Gradle task uploads the release JAR to the HomeCore project (ID `1711592`). It runs the project checks before uploading and tags the file with the Minecraft and NeoForge versions configured above.

Create an API token in your CurseForge account settings under **API Tokens**. Store it in your user-level Gradle properties file, outside this repository:

```properties
curseforge_api_token=YOUR_PRIVATE_CURSEFORGE_TOKEN
```

On Windows, this file is `%USERPROFILE%\.gradle\gradle.properties`; on Linux/macOS, use `~/.gradle/gradle.properties`. Alternatively, set the `CURSEFORGE_API_TOKEN` environment variable. Never commit or share the token.

When you are ready to upload a release, run:

```powershell
./gradlew.bat publishHomeCoreToCurseForge
```

CurseForge may review the uploaded file before making it available on the project page.

## HomeLink components

### HomeLink Circuit Board

A basic electronic component for HomeLink-compatible devices. It is assembled exclusively at the Electronics Workbench from vanilla Copper, Redstone and Quartz. Each assembly yields two boards.

### HomeLink Microprocessor

An advanced processing component for more complex HomeLink devices. It is assembled exclusively at the Electronics Workbench using a HomeLink Circuit Board and vanilla Gold Nuggets, Copper, Redstone and Quartz.

### HomeLink Communication Module

A communication, networking and sensing component for HomeLink devices that transmit data or detect signals. It is assembled exclusively at the Electronics Workbench from a HomeLink Circuit Board, a HomeLink Microprocessor and vanilla Copper, Redstone, Quartz and an Amethyst Shard.

### HomeLink Control Module

A machine control and automation component for HomeLink devices that act on the world: motors, mechanisms and automated machines. It is assembled exclusively at the Electronics Workbench from a HomeLink Circuit Board, a HomeLink Microprocessor, a Comparator and vanilla Copper, Redstone and Iron.

### HomeLink Electronics Components

| Component | ID | Role |
| --- | --- | --- |
| Circuit Board | `homecore:homelink_circuit_board` | Basic electronics. |
| Microprocessor | `homecore:homelink_microprocessor` | Processing and logic. |
| Communication Module | `homecore:homelink_communication_module` | Communication, networking and sensing. |
| Control Module | `homecore:homelink_control_module` | Machine control and automation. |

All four are made in the Electronics Workbench according to their current recipes, and none has a vanilla crafting-table recipe. The two modules are specialised components, not higher tiers of the board or processor. These IDs are stable: consumer mods can reference them directly without depending on each other. HomeCore itself does not depend on any consumer mod.

## HomeLink Electronics Workbench

`homecore:electronics_workbench` is the shared assembly station for HomeLink electronics. It requires no energy or fuel. Craft it in a vanilla crafting table with this pattern:

```text
I R I    I = Iron Ingot       R = Redstone
C W C    C = Copper Ingot     W = Crafting Table
P P P    P = Any Planks
```

The workbench occupies two adjacent blocks with a continuous countertop and one shared inventory. Leave both spaces clear when placing it. Either half opens the same interface; breaking either half returns one workbench and its contents in survival mode. Existing single-block workbenches remain usable with their saved contents; break and replace one to get the wider shape.

Its steel frame, recessed interface panels and warm metallic accents follow HomeLink Farm's visual style. The shared steel textures are bundled with HomeCore; HomeLink Farm is not required.

Place materials in the nine input slots grouped on the left, select a component from its icon tab and choose the number of **finished items** (up to 64). Quantities follow the recipe yield: circuit boards increase in pairs, while microprocessors and modules increase individually. Quick quantity buttons and MAX make it easier to select a batch. Ingredient icons show available and required counts before assembly. The output slot remains visible beside the material grid.

Assemble one prototype per batch using drag and drop, or click a part and then its matching socket. Correct placements snap into place; mistakes consume no materials. A short production animation follows validation, then the completed batch appears in the output slot.

The server reserves ingredients before the prototype begins and validates every placement. Cancelling, closing the prototype screen or disconnecting before validation returns reserved materials to the input slots. Interrupted prototypes are also refunded after a world reload. Validated production continues after the screen closes and resumes when the workbench is loaded again. Its inventory is locked while a batch is reserved. Breaking the workbench drops stored items, finished output and reserved ingredients from any unfinished batch. None of the four components can be crafted in a vanilla crafting table.

Other HomeLink mods can add recipes through the `homecore:electronics` recipe type in their own data resources; they do not need to depend on each other. The existing Device, Metric, Action, Event and HomeNetwork contracts are unchanged.

See [Electronics recipe format](docs/ELECTRONICS.md) for the extensible JSON schema.

For a ready-to-paste CurseForge project description, see [CURSEFORGE_DESCRIPTION.md](CURSEFORGE_DESCRIPTION.md).

## Contracts and lifecycle

Public contracts are in `fr.lkdm.homecore.api.*`. Javadoc is generated at `build/docs/javadoc/index.html`. Consumers do not need to import implementation packages outside `api`.

A `DashboardDevice` is a logical device: it can be a BlockEntity, a game system, or a machine with no position. Its UUID must remain stable across save and reload. Its type, metrics and actions must remain stable while it is registered; their values can change.

Minimal implementation, with types imported from the API:

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

On the server thread, call `DashboardAPI.devices(server).register(device)`, then `unregister(device.id())` when its source is removed or unloaded. The registry does not scan chunks. `isValid()` also discards a removed object when queried. Duplicate UUIDs are rejected.

The [complete compilable example](src/examples/java/fr/lkdm/homecore/example/ExampleMachineDevice.java) demonstrates metrics, actions, events, a provider, a schema and a capability. It is kept out of the distributed JAR. Its in-memory state is for demonstration; a real machine should save its identity and values through its own persistence.

## Metrics and schema

```java
private final DeviceMetric<Double> temperature = DeviceMetric.builder(
        ResourceLocation.fromNamespaceAndPath("examplemod", "temperature"),
        Component.literal("Temperature"), MetricTypes.DOUBLE, 22.5)
    .unit(fr.lkdm.homecore.api.metric.Unit.CELSIUS)
    .updatePolicy(UpdatePolicy.NORMAL)
    .build();

@Override public List<DeviceMetric<?>> metrics() { return List.of(temperature); }
```

`temperature.setValue(23.0)` validates the value and increments its revision only when the value changes. `MetricRange` uses inclusive bounds and an exact step; zero means continuous. Types include booleans, integers, longs, finite doubles, text, enumerations, percentages, durations, positions, items, fluids and energy. Items and fluids carry an ID and quantity, not a complete inventory.

Units can be extended with `new Unit(id, symbol)`. A custom `MetricType` can be used locally; to expose its value over the network, the value must use one of the representations supported by `WireValue`.

`device.schema()` describes metrics, actions and events for building an interface. Keep and reuse their instances; do not recreate them on every call. REALTIME and ON_CHANGE are checked every tick, FAST every 5 ticks, NORMAL every 20, and SLOW every 100. Only changed revisions are sent. STATIC appears only in snapshots.

## Actions and permissions

With `progress` as a double metric exposed by the device:

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

Expose this instance through `actions()`. Type, bounds, step, SELECT options and additional predicates are checked before the handler runs. Buttons use `fr.lkdm.homecore.api.action.Unit.INSTANCE`. Validators must be pure. Handlers run on the calling thread.

For a player request, call this on the server thread:

```java
ActionResult result = DashboardAPI.executeAction(
    serverPlayer, networkId, deviceId, actionId, 73.0);
```

Identity comes from the authenticated `ServerPlayer`. Do not expose `DeviceAction.execute` directly to remote requests: it validates the parameter but does not grant authorization.

The gateway checks existence, network membership, CONTROL, action-specific permission, ONLINE status and the parameter. Unknown permissions are denied. The shared per-player limit allows a burst of 10 requests and refills at 10 per second. Rejected or invalid requests also use this budget; reconnecting does not reset it.

| Role | Default permissions |
| --- | --- |
| OWNER | All recognized permissions |
| ADMIN | VIEW, CONTROL, AUTOMATE, CONFIGURE, MANAGE_NETWORK |
| MEMBER | VIEW, CONTROL, AUTOMATE |
| VIEWER | VIEW |

`DashboardAPI.hasPermission` checks a current permission. Direct Java mutation of a network is a trusted server API: a mod that exposes it to players must check MANAGE_NETWORK itself. A custom `PermissionValidator` can be used in a gateway built by the consumer; it does not automatically replace HomeCore transport policy.

## Persistent networks

`DashboardAPI.networks(server)` exposes `createNetwork`, `deleteNetwork`, `getNetwork`, `getNetworksForPlayer`, `addDevice`, `removeDevice`, `getDevices`, `setMember` and `removeMember` on the server thread. Snapshots are immutable. These methods cannot remove or demote the owner.

Networks use Overworld SavedData shared across dimensions. Device UUIDs survive restarts; integrations register their Java objects again when they load. Consumers can use CONNECTED, UNREACHABLE and OFFLINE; HomeCore does not calculate radio range.

## Events

Declare IDs in `DashboardDevice.eventTypes()`, then publish on the server thread:

```java
DashboardAPI.events(server).publish(new DeviceEvent(
    eventId, device.id(), Instant.now(), DeviceEvent.Severity.INFO,
    Map.of("state", "completed")));
```

`subscribe(listener)` accepts multiple listeners and returns an `AutoCloseable` subscription. Close it when tearing down the consumer to release references. The bus closes when the server stops. Structured data consists of named text fields, not executable objects. The network sends an event to subscribers authorized to view its device.

## BlockEntity provider

During common initialization of the consumer mod:

```java
DashboardAPI.registerDeviceProvider(MY_BLOCK_ENTITY_TYPE.get(),
    blockEntity -> new MyMachineDevice(blockEntity));
```

`MyMachineDevice` implements `DashboardDevice`. When the BlockEntity loads on the server:

```java
DashboardAPI.providers().discover(blockEntity)
    .ifPresent(device -> DashboardAPI.devices(server).register(device));
```

Avoid duplicate registrations and unregister the device when it unloads. A provider does not install a scanner; `discover` does not register its result automatically. The overload with `Class<T>` explicitly checks the runtime type; the other relies on the generic association of `BlockEntityType<T>`.

## Capabilities

Define a Java contract and register it once with `DashboardAPI.capabilities().register(new DeviceCapability<>(id, MyCapability.class))`. Keep the returned `DeviceCapability<MyCapability>`. In the device, build `CapabilitySet.builder().add(descriptor, implementation).build()`, return its `ids()` from `capabilities()` and delegate `capability(descriptor)` to `query`.

`device.capability(descriptor)` returns a typed `Optional`. The implementation stays local to the server; only IDs are sent over the network. HomeCore does not impose a complete energy or inventory system.

## Dashboard client

For a dashboard, use `HomeCoreClient.subscribeNetwork(networkId)`: an active subscription for up to 128 devices, with initial snapshots spread across multiple ticks followed by deltas only. `NetworkWatchResponse.truncated()` explicitly reports networks larger than this limit. Membership and permission changes are sent without reloading devices that remain; events cover the entire network the player is authorized to see. HomeCore 1.7.0 uses network protocol 2, which must match on both sides. The older paginated API remains available.

From client code only, `HomeCoreClient.requestDevices(Optional.empty(), 0)` requests visible networks. Then pass `Optional.of(networkId)` to receive devices, snapshots and changes. A page contains at most 16 IDs; use `nextOffset` to continue. Only one device page is active per player.

`HomeCoreClient.executeAction(networkId, deviceId, actionId, parameter)` returns the correlation UUID. `ClientDeviceCache.INSTANCE.listen(...)` observes responses, results and deltas. Close the listener and call `unsubscribe(networkId)` when the dashboard no longer needs it. Disconnecting clears state and listeners.

The cache keeps the initial snapshot in `devices()` and the latest changes in `metricUpdates()`: apply deltas by key and revision to display current state. It does not rewrite the initial NBT on each change. See the [transport contract](docs/PROTOCOL.md).

## Development and verification

Run `./gradlew.bat runDebugClient`, then use `/homecore_debug` to install the development device. It exposes temperature, energy, enabled state, progress and a counter, along with the `toggle_enabled`, `set_progress` and `reset_counter` actions and the `homecore:test_event` event. The development mod, examples and fixtures are excluded from the main release JAR.

```powershell
./gradlew.bat test
./gradlew.bat runPersistence -PpersistencePass=write
./gradlew.bat runPersistence -PpersistencePass=read
./gradlew.bat runNetworkSmoke
./gradlew.bat runDebugSmoke
./gradlew.bat runWorkbenchSmoke
./gradlew.bat runWorkbenchSmoke -PworkbenchLanguage=fr_fr
```

The development smoke test also checks the command, `set_progress` action and event notification. JUnit tests cover the five initial values and three actions. The persistence passes use the same isolated world. The client smoke test creates an isolated integrated world, checks discovery, snapshot, action, result and delta without an additional snapshot, then closes the client. Completed validations are documented in [VALIDATION.md](docs/VALIDATION.md).

## Reachability constraints

`HomeNetworkManager.setReachabilityPolicy(id, predicate)` installs an ephemeral link constraint that must be set again when the server starts. Each policy receives the network and device. `isReachable` checks membership and these constraints; an exception denies the link. Lists, snapshots, deltas, events and actions use this decision along with permissions. HomeCore does not calculate range; that logic belongs to the consumer. With no policy, the existing logical behavior is preserved. Network protocol remains 2.

## Renaming networks

`HomeNetworkManager.renameNetwork(id, name)` preserves the UUID, members, devices and creation date. Network snapshots and saved data reflect the new name. This API is for trusted server code: calls originating from a player must check MANAGE_NETWORK before mutation. Names must contain 1 to 128 characters and cannot be blank.

## Device binding

A `DashboardDevice` whose block records its network can implement `NetworkMember`: recorded network, owner, machine-specific right (`canConfigure`) and the `homeNetworkChanged` notification. A dashboard can then list a machine's binding and move it without desynchronizing its block.

For a player request, call `DashboardAPI.bindDevice(player, device, Optional.of(networkId))`, or `Optional.empty()` to detach. HomeCore requires `canConfigure` and MANAGE_NETWORK on the destination and on the previous network. The device joins the destination before leaving the old network; nothing changes when a check fails. A device without `NetworkMember` returns `NOT_SUPPORTED`. API 1.5.0.
