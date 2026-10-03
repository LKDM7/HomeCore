# HomeCore transport v2

The public payloads live in `fr.lkdm.homecore.api.transport`. NeoForge negotiates
protocol `2` in HomeCore 1.14.0. Both peers must use the matching protocol; existing
Java page APIs and value discriminants remain available. Payload registration is direction-specific and all handlers enter
the receiving game's main thread before accessing state.

## Discovery and subscriptions

`DeviceListRequest` contains a correlation UUID, an optional network UUID and a
nonnegative offset. An absent network requests the caller's accessible networks;
a present network requests its device IDs. `DeviceListResponse` provides the page,
next offset and result. A page contains at most 16 identities. Network metadata or
device snapshots follow the response. A device page subscribes the caller to that
page only; a subsequent page replaces the previous subscription.

Dashboard consumers should use `HomeCoreClient.subscribeNetwork(networkId)` for
`NetworkWatchRequest`. `NetworkWatchResponse` returns up to 128 sorted device IDs,
the total available count, an explicit `truncated` flag and a result. Initial
snapshots follow over bounded server ticks. Subsequent successful responses with
the same correlation UUID replace only the roster: retained devices keep their
snapshots and deltas; removed IDs are pruned; only new IDs receive initial snapshots.
A manual subscription refresh creates a new correlation UUID and streams a fresh
snapshot. One watch or one legacy page is active per player, never both.

Registered-device membership is checked every 20 ticks, and immediately when the
immutable HomeNetwork changes. Owner/member/role metadata changes are pushed;
VIEW permission is checked on every tick and every emitted event. Network watches
receive events for all authorized network sources, including sources outside the
128-device watched roster. This does not transfer extra device state.

`Unsubscribe` releases a network subscription. Disconnect and server shutdown also
release subscriptions. Client caches are cleared on disconnect. Permission
revocation invalidates the subscription and clears the corresponding client data.

## State and changes

`DeviceSnapshot` carries a defensive NBT copy of the device identity, schema and
current metric values. A snapshot is bounded to 64 KiB. `MetricUpdate` carries one
metric ID, revision and a tagged immutable value. Revisions prevent old updates
from overwriting newer state. Refresh policies control sampling; unchanged values
are not transmitted. STATIC values are part of the initial snapshot only.

`HomeNetworkSnapshot` exposes network metadata. `DeviceEventNotification` carries
a typed event identifier, source, timestamp, severity and bounded named string
fields. Events and updates require current VIEW access.

## Commands

`ExecuteActionRequest` carries request/network/device/action identities and a
tagged parameter. There is no player UUID in the request: the server obtains it
from the authenticated connection. The action gateway checks membership, CONTROL,
additional permission, availability and parameter validity before invoking a
handler. Invalid and denied attempts also consume the per-player request budget.

`ActionResultResponse` preserves the request UUID and returns the structured
outcome. Enum selections resolve only against server-declared options. No class
name from the wire is loaded and no arbitrary Java object is deserialized.

Consumers should normally use `api.client.HomeCoreClient` rather than manually
constructing packets. Direct Java registry/network mutation is a trusted server
API and must never be exposed to remote players without authorization.

## Snapshot fields

Names, descriptions, status explanations and unit symbols are truncated to their
display bounds without splitting UTF-16 surrogate pairs. Overlong presentation
text never discards otherwise valid metrics/actions. Machine identifiers, collection
limits and total snapshot budgets remain strict. Text is plain on the wire. Java API definitions
retain Minecraft Components. Device snapshot compounds contain:

| Field | Meaning |
| --- | --- |
| `id`, `type`, `name` | Device UUID, namespaced kind, display name |
| `status`, optional `message` | Availability and explanation |
| optional `x`, `y`, `z`, `dimension` | Location when the device has one |
| optional `powered` | On/off state of a `Switchable` device (HomeCore 1.11.0) |
| `metrics` | Compounds containing `id`, `name`, `type`, `unit`, `unitSymbol`, `policy`, optional `range`, `value`, `revision` |
| `actions` | Compounds containing `id`, `name`, `description`, `type`, `permission`, optional `min`/`max`/`step`, `maxLength`, `options` |
| `events`, `capabilities` | Lists of namespaced identifiers |

A device implementing `Switchable` or `Renamable` also lists the standard actions `homecore:power` and `homecore:rename`. A change of `name` or `powered` resends the whole device snapshot, like a status change.

Metric ranges encode exact decimal `min`, `max`, `step` as strings. Each value
compound uses the documented `WireValue.Kind` discriminant `kind`; use
`WireValue.fromTag` instead of interpreting type-specific fields manually. Numeric
long values remain exact; enum values carry names, not Java class names.

Network metadata contains `id`, `name`, `owner`, `createdAtSeconds`,
`createdAtNanos`, `memberCount`, `deviceCount` and the requesting player's `role`.
Large membership sets are not duplicated into every response.

Definitions must remain stable while a device is registered. Replace its registered
instance when changing its schema. Value changes use `DeviceMetric.setValue`.
Sampling intervals in server ticks are REALTIME/ON_CHANGE=1, FAST=5, NORMAL=20,
SLOW=100; STATIC sends no deltas. There is no background world scan.

## Consumer cache and subscription replacement

`ClientDeviceCache.devices()` keeps initial snapshots. Overlay `metricUpdates()`
by `DeviceKey(networkId, deviceId)` and metric ID to display current values. The
cache indexes initial revisions once and rejects unknown metrics and stale deltas.
`listen` returns a disposable handle; close it when its consumer is destroyed.
Disconnect clears snapshots, revisions, recent messages and listeners. Unsubscribe
and permission revocation also purge recent sensitive data for that network.

One watch or device page is active per player. Successful replacement prunes the previous
page; a successful directory request stops device updates. Denied or failed
requests for another network retain the prior subscription. Rate-limited requests
do not reset it. A denied/failed active network is invalidated immediately. The
cache retains at most 16 network summaries, 256 device snapshots, 128 recent
messages and 128 listeners; incoming device schemas contain at most 128 metrics.
An absent delta means no new sample, not necessarily an unchanged remote machine.

## Transport limits

- Discovery: 16 identities per page, burst 2 requests and refill 2/second/player.
- Active subscriptions: at most 1024 players, one watch or device page each.
- Network watch: 128 devices, explicit truncation beyond the cap; at most 16
  snapshots and 256 metric deltas per server tick/player. Delivery rotates after
  budget exhaustion, including per-device metric cursors, so hot devices cannot
  permanently starve later devices. The page discovery API remains capped at 16.
- Control: burst 10 requests and refill 10/second/player, including denied attempts.
- Snapshots: 64 KiB encoded data and decoded NBT allocation budget; at most 128
  metrics, actions, event IDs and capability IDs per device.
- Wire text: at most 4096 UTF-16 units; action metadata advertises the smaller of
  its declared text limit and 4096. Local Java actions may declare up to 32767.
- Namespaced identifiers: at most 256 UTF-16 units, including item/fluid IDs.
- Event delivery: at most 16 per server tick and 32/second/player; events whose
  aggregate field names and values exceed 8192 UTF-16 units are not delivered.
  The local event contract allows 64 fields, keys up to 128 and values up to 4096.
- SELECT: at most 256 declared immutable scalar/enum options.

Custom `MetricType` contracts are extensible within Java, but wire values use the
closed `WireValue.Kind` representations. An unsupported custom representation or
oversized snapshot fails legacy page discovery with FAILED. Network watches isolate
that device behind an ERROR placeholder with empty metrics/actions and retry at
most once per 20 ticks; healthy devices keep streaming. Repeated failure does not
resend an unchanged placeholder. Definitions arriving for new metric IDs refresh
the bounded schema instead of growing the revision map indefinitely.
Longs remain exact. Java enum class names, action handlers and capability objects
never cross the connection. Item and fluid values carry IDs and amounts, not full
Minecraft stacks or their components. Names/descriptions are plain text snapshots;
translation and formatting remain available in the local Java Component API.
