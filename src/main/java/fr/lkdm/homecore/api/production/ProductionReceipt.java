package fr.lkdm.homecore.api.production;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server-issued proof that a batch of items was really finished.
 *
 * <p>A receipt is emitted at the moment the result exists, never when a job starts,
 * when materials are reserved, when a preview is shown or when someone later takes
 * the output out of a slot. A cancelled or refunded batch produces no receipt at
 * all, and taking a finished output out three times still produces exactly one.</p>
 *
 * <p>{@code quantity} is carried by {@link #result} and counts finished items, not
 * operations: a batch that assembles sixty-four components reports sixty-four.
 * {@link #transactionId} is stable for the batch, so a consumer that sees the same
 * receipt twice — after a reload, or because a producer re-emitted it — must credit
 * it once. Ordering uses server game ticks, which advance monotonically and are
 * unaffected by {@code /time set}; {@link #sequence} orders receipts issued during
 * the same tick within one server run.</p>
 *
 * @param transactionId stable identity of the finished batch
 * @param producer player whose recorded authorship the batch was started under
 * @param source namespaced producer kind, for example {@code homecore:electronics_workbench}
 * @param recipe recipe the batch was started from, when the producer records one
 * @param result finished items; its count is the real produced quantity
 * @param startedTick server game tick at which the batch was started
 * @param completedTick server game tick at which the result actually existed
 * @param sequence intra-tick ordering within the current server run
 * @param network network context of the producing device, when it has one
 * @param start durable epoch and ordinal of the batch start, absent for legacy producers
 */
public record ProductionReceipt(UUID transactionId, UUID producer, ResourceLocation source,
                                Optional<ResourceLocation> recipe, ItemStack result,
                                long startedTick, long completedTick, long sequence,
                                Optional<UUID> network, Optional<ProductionStart> start) {
    /** Compatibility constructor for producers that do not record intra-tick start order.
     * @param transactionId stable batch identity
     * @param producer recorded author
     * @param source producer kind
     * @param recipe recorded recipe
     * @param result finished items
     * @param startedTick server tick at start
     * @param completedTick server tick at completion
     * @param sequence completion order
     * @param network producing device's network
     */
    public ProductionReceipt(UUID transactionId, UUID producer, ResourceLocation source,
                             Optional<ResourceLocation> recipe, ItemStack result, long startedTick,
                             long completedTick, long sequence, Optional<UUID> network) {
        this(transactionId, producer, source, recipe, result, startedTick, completedTick, sequence, network, Optional.empty());
    }
    /** Validates the receipt and copies the produced stack so observation cannot mutate it. */
    public ProductionReceipt {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(result, "result");
        if (result.isEmpty()) throw new IllegalArgumentException("A receipt must describe produced items");
        if (startedTick < 0 || completedTick < startedTick || sequence < 0) {
            throw new IllegalArgumentException("A receipt must be ordered after its own start");
        }
        result = result.copy();
    }

    /** Returns a fresh copy of the produced items.
     * @return produced stack, never the producer's own instance
     */
    @Override
    public ItemStack result() { return result.copy(); }

    /** Returns the real number of finished items.
     * @return produced quantity
     */
    public int quantity() { return result.getCount(); }
}
