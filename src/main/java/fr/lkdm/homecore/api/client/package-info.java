/** Dashboard request helpers and bounded session snapshots.
 * {@link fr.lkdm.homecore.api.client.HomeCoreClient} is client-only and requires
 * the client thread. Its cache retains initial snapshots plus revision deltas;
 * consumers overlay the deltas when rendering and close listener handles on disposal.
 */
package fr.lkdm.homecore.api.client;
