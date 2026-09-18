# Delivery validation

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
