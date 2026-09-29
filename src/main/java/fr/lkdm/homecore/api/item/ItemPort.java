package fr.lkdm.homecore.api.item;

import net.neoforged.neoforge.items.IItemHandler;

/**
 * Shared item endpoint, using NeoForge's slot, remainder and simulation semantics.
 * Calls belong on the server thread. INPUT rejects extraction, OUTPUT rejects insertion,
 * and BOTH allows either operation. Implementations must never mutate offered stacks or
 * their contents during simulation. Returned inventory views must not be modified by callers.
 */
public interface ItemPort extends IItemHandler {
    /** Describes item movement relative to this machine.
     * @return allowed transfer direction
     */
    ItemPortType type();
}
