/**
 * Persistent logical home networks, memberships and device identities.
 * Managers expose trusted server operations; consumers must validate remote
 * requests before mutation. Immutable snapshots may be retained across updates.
 * Optional server-owned reachability policies can constrain delivery and actions.
 * Range calculations and antenna simulation belong to the policy integration.
 */
package fr.lkdm.homecore.api.network;
