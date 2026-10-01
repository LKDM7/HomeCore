/**
 * Neutral notification of batches that were really produced.
 *
 * <p>A producer publishes a {@link fr.lkdm.homecore.api.production.ProductionReceipt}
 * when a result exists; a consumer such as a task tracker credits it once. Possessing,
 * withdrawing or picking up an item is not production and issues no receipt.</p>
 */
package fr.lkdm.homecore.api.production;
