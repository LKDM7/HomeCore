/**
 * Neutral, read-only stock reading contract.
 *
 * <p>A storage mod publishes {@link fr.lkdm.homecore.api.stock.StockProvider} as a
 * device capability; a consumer such as a planner reads it without importing any
 * storage implementation. Nothing here extracts, reserves or modifies an inventory.</p>
 */
package fr.lkdm.homecore.api.stock;
