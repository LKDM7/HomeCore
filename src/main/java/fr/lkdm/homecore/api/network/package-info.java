/**
 * Persistent logical home networks, memberships and device identities.
 * Managers expose trusted server operations; consumers must validate remote
 * requests before mutation. Immutable snapshots may be retained across updates.
 * No world scans, range calculations or antenna simulation are performed.
 */
package fr.lkdm.homecore.api.network;
