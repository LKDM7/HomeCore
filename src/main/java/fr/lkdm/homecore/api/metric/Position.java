package fr.lkdm.homecore.api.metric;

/** Immutable block coordinates, interpreted in the device's dimension.
 * @param x block X coordinate
 * @param y block Y coordinate
 * @param z block Z coordinate
 */
public record Position(int x, int y, int z) { }
