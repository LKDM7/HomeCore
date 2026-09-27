package fr.lkdm.homecore.api.energy;

import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;

/**
 * Input-only {@link EnergyRole#CONSUMER CONSUMER} port for machines that need HE to run.
 * Energy networks fill the buffer; the machine draws from it and stops when it is empty.
 * Server thread only. The capacity is read on every call so it may follow a server config.
 */
public final class EnergyBuffer implements EnergyPort {
    private final LongSupplier capacity;
    private final Runnable onChange;
    private long stored;

    /**
     * Creates an empty buffer.
     *
     * @param capacity most HE held; negative values count as zero
     * @param onChange called after the stored amount changed, typically {@code setChanged}
     */
    public EnergyBuffer(LongSupplier capacity, Runnable onChange) {
        this.capacity = capacity;
        this.onChange = onChange;
    }

    @Override
    public EnergyRole role() {
        return EnergyRole.CONSUMER;
    }

    @Override
    public EnergyPortType type() {
        return EnergyPortType.INPUT;
    }

    @Override
    public long stored() {
        return Math.min(stored, capacity());
    }

    @Override
    public long capacity() {
        return Math.max(0, capacity.getAsLong());
    }

    @Override
    public long insert(long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long accepted = Math.min(amount, Math.max(0, capacity() - stored));
        if (!simulate && accepted > 0) {
            stored += accepted;
            onChange.run();
        }
        return accepted;
    }

    @Override
    public long extract(long amount, boolean simulate) {
        return 0;
    }

    /**
     * Draws energy for the machine's own use.
     *
     * @param amount HE needed; zero or less always succeeds
     * @return whether the whole amount was available and has been used; nothing is used otherwise
     */
    public boolean consume(long amount) {
        if (amount <= 0) return true;
        if (stored() < amount) return false;
        stored = stored() - amount;
        onChange.run();
        return true;
    }

    /**
     * Pays a running cost spread over a period, such as "{@code perPeriod} HE every {@code period}
     * ticks", so costs below 1 HE/t stay exact: over any {@code period} consecutive ticks exactly
     * {@code perPeriod} HE is used. The machine is powered on a tick only while the buffer holds
     * at least 1 HE and the share of that tick.
     *
     * @param perPeriod HE used over one period; zero or less means the machine needs no energy
     * @param period length of the period in ticks, at least 1
     * @param gameTime current game time
     * @return whether the machine is powered on this tick
     */
    public boolean draw(long perPeriod, int period, long gameTime) {
        if (perPeriod <= 0) return true;
        long share = shareOf(perPeriod, period, gameTime);
        if (stored() < Math.max(1, share)) return false;
        return consume(share);
    }

    /**
     * Share of a periodic cost due on one tick.
     *
     * @param perPeriod HE used over one period
     * @param period length of the period in ticks
     * @param gameTime current game time
     * @return the HE due on this tick: {@code floor((k+1)·P/n) - floor(k·P/n)} with {@code k = gameTime mod n}
     */
    public static long shareOf(long perPeriod, int period, long gameTime) {
        if (perPeriod <= 0) return 0;
        int n = Math.max(1, period);
        long k = Math.floorMod(gameTime, n);
        long rest = perPeriod % n;
        return perPeriod / n + rest * (k + 1) / n - rest * k / n;
    }

    /**
     * Charge level for displays.
     *
     * @return charge from 0 to 100
     */
    public int percent() {
        long cap = capacity();
        return cap <= 0 ? 0 : (int) Math.min(100, stored() * 100 / cap);
    }

    /**
     * Sets the stored amount, clamped to the current capacity, without notifying.
     *
     * @param amount new stored amount
     */
    public void setStored(long amount) {
        stored = Math.max(0, Math.min(amount, capacity()));
    }

    /**
     * Writes the stored amount.
     *
     * @param tag tag receiving the stored amount
     * @param key entry name
     */
    public void save(CompoundTag tag, String key) {
        tag.putLong(key, stored());
    }

    /**
     * Reads the stored amount; a missing entry means empty.
     *
     * @param tag tag holding the stored amount
     * @param key entry name
     */
    public void load(CompoundTag tag, String key) {
        stored = Math.max(0, tag.getLong(key));
    }
}
