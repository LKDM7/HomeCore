package fr.lkdm.homecore.api.energy;

/**
 * Energy port exposed by a block on one of its faces, measured in {@link EnergyApi#HE HE}.
 * HomeCore defines only this contract: producing, storing, transporting and consuming
 * energy belong to the mods that implement it. All calls happen on the server thread.
 *
 * <p>Transfers are exact: a caller first simulates, then performs the transfer with the
 * simulated amount, and debits or credits the other side with exactly the returned value.
 * Implementations never return a negative amount, never accept more than requested and never
 * create or destroy energy during a transfer.
 */
public interface EnergyPort {
    /**
     * Role of the device behind this port, used by distribution networks to route energy
     * from producers to consumers first and to keep storage from feeding storage.
     *
     * @return device role
     */
    EnergyRole role();

    /**
     * Direction allowed through this port.
     *
     * @return port direction
     */
    EnergyPortType type();

    /**
     * Energy currently held behind this port.
     *
     * @return stored energy, from zero to {@link #capacity()}
     */
    long stored();

    /**
     * Maximum energy held behind this port.
     *
     * @return nonnegative capacity
     */
    long capacity();

    /**
     * Offers energy to this port.
     *
     * @param amount offered energy; a nonpositive amount accepts nothing
     * @param simulate whether to only report the amount that would be accepted
     * @return accepted energy, from zero to {@code amount}; always zero for an output-only port
     */
    long insert(long amount, boolean simulate);

    /**
     * Takes energy from this port.
     *
     * @param amount requested energy; a nonpositive amount returns nothing
     * @param simulate whether to only report the amount that would be extracted
     * @return extracted energy, from zero to {@code amount}; always zero for an input-only port
     */
    long extract(long amount, boolean simulate);

    /**
     * Energy this port would accept right now: the {@code maximumEnergyRequested} of a consumer.
     *
     * @return nonnegative requested energy
     */
    default long requested() {
        return type().canReceive() ? insert(Long.MAX_VALUE, true) : 0;
    }

    /**
     * Energy this port could deliver right now.
     *
     * @return nonnegative available energy
     */
    default long available() {
        return type().canSend() ? extract(Long.MAX_VALUE, true) : 0;
    }
}
