/** Server-owned events with bounded named text fields and removable listeners.
 * Publish Minecraft-related changes on the server thread and close subscriptions
 * when their consumers stop. Network delivery separately verifies visibility.
 */
package fr.lkdm.homecore.api.event;
