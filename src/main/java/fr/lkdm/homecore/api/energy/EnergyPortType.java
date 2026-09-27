package fr.lkdm.homecore.api.energy;

/** Direction of energy allowed through an {@link EnergyPort}. */
public enum EnergyPortType {
    /** ENERGY_INPUT: the port only receives energy. */ INPUT,
    /** ENERGY_OUTPUT: the port only delivers energy. */ OUTPUT,
    /** ENERGY_INPUT and ENERGY_OUTPUT on the same face. */ BOTH;

    /** Whether energy may enter through this port.
     * @return true for INPUT and BOTH
     */
    public boolean canReceive() { return this != OUTPUT; }

    /** Whether energy may leave through this port.
     * @return true for OUTPUT and BOTH
     */
    public boolean canSend() { return this != INPUT; }
}
