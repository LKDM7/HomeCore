package fr.lkdm.homecore.api.energy;

/** Role of the device behind an {@link EnergyPort} in a distribution network. */
public enum EnergyRole {
    /** Generates energy and offers it through its output. */ PRODUCER,
    /** Stores energy it received and returns it later; never generates any. */ STORAGE,
    /** Uses energy it receives. */ CONSUMER
}
