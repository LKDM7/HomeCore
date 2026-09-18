/** Server authorization and bounded per-player request budgets.
 * Gateways accept authenticated identities from trusted server code. Network
 * membership is read for every request; direct registry or network mutations
 * remain trusted operations whose callers must authorize their own users.
 */
package fr.lkdm.homecore.api.security;
