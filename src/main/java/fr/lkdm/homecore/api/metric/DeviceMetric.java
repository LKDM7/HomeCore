package fr.lkdm.homecore.api.metric;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A strongly typed observable value with immutable metadata.
 * Updates and snapshots are synchronized. Values supplied by custom types must be immutable.
 * A policy is only scheduling metadata; no background worker is created.
 * @param <T> immutable value type
 */
public final class DeviceMetric<T> {
    private final ResourceLocation id;
    private final Component displayName;
    private final MetricType<T> type;
    private final Unit unit;
    private final UpdatePolicy updatePolicy;
    private final MetricRange range;
    private T value;
    private long revision;

    private DeviceMetric(Builder<T> builder) {
        id = builder.id; displayName = builder.displayName.copy(); type = builder.type;
        unit = builder.unit; updatePolicy = builder.updatePolicy; range = builder.range;
        if (range != null && !type.numeric()) throw new IllegalArgumentException("Range requires a numeric metric");
        value = checked(builder.initialValue);
    }

    /** Creates a builder and checks all arguments when built.
     * @param id stable metric ID within the device
     * @param displayName user-facing label
     * @param type runtime value contract
     * @param initialValue initial immutable value
     * @param <T> value type
     * @return builder
     */
    public static <T> Builder<T> builder(ResourceLocation id, Component displayName, MetricType<T> type, T initialValue) {
        return new Builder<>(id, displayName, type, initialValue);
    }
    /** Returns the metric ID.
     * @return metric ID
     */
    public ResourceLocation id() { return id; }
    /** Returns a defensive copy of the label.
     * @return display name
     */
    public Component displayName() { return displayName.copy(); }
    /** Returns the runtime type contract.
     * @return metric type
     */
    public MetricType<T> type() { return type; }
    /** Returns the display unit.
     * @return unit
     */
    public Unit unit() { return unit; }
    /** Returns the requested refresh policy.
     * @return scheduling hint
     */
    public UpdatePolicy updatePolicy() { return updatePolicy; }
    /** Returns the numeric range, if any.
     * @return optional range
     */
    public Optional<MetricRange> range() { return Optional.ofNullable(range); }
    /** Returns the current immutable value.
     * @return current value
     */
    public synchronized T value() { return value; }
    /** Returns the value change revision, initially zero.
     * @return revision
     */
    public synchronized long revision() { return revision; }
    /** Returns a consistent value and revision pair.
     * @return atomic snapshot
     */
    public synchronized Snapshot<T> snapshot() { return new Snapshot<>(value, revision); }
    /** Changes the value after validation; equal values do not increment the revision.
     * @param newValue candidate immutable value
     * @return whether the value changed
     * @throws IllegalArgumentException if the value violates its type or range
     */
    public synchronized boolean setValue(T newValue) {
        T checked = checked(newValue);
        if (Objects.equals(value, checked)) return false;
        long nextRevision = Math.incrementExact(revision);
        value = checked; revision = nextRevision;
        return true;
    }
    private T checked(T candidate) {
        T checked = type.validate(candidate);
        if (range != null && !range.accepts(type.number(checked).orElseThrow())) throw new IllegalArgumentException("Metric value outside range or step");
        return checked;
    }

    /** Consistent immutable value and revision pair.
     * @param value immutable metric value
     * @param revision change sequence
     * @param <T> value type
     */
    public record Snapshot<T>(T value, long revision) { }

    /** Builder for metric metadata.
     * @param <T> immutable value type
     */
    public static final class Builder<T> {
        private final ResourceLocation id;
        private final Component displayName;
        private final MetricType<T> type;
        private final T initialValue;
        private Unit unit = Unit.NONE;
        private UpdatePolicy updatePolicy = UpdatePolicy.NORMAL;
        private MetricRange range;

        private Builder(ResourceLocation id, Component displayName, MetricType<T> type, T initialValue) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = Objects.requireNonNull(displayName, "displayName").copy();
            this.type = Objects.requireNonNull(type, "type");
            this.initialValue = initialValue;
        }
        /** Sets the unit.
         * @param unit display unit
         * @return this builder
         */
        public Builder<T> unit(Unit unit) { this.unit = Objects.requireNonNull(unit, "unit"); return this; }
        /** Sets the scheduling hint.
         * @param policy refresh policy
         * @return this builder
         */
        public Builder<T> updatePolicy(UpdatePolicy policy) { updatePolicy = Objects.requireNonNull(policy, "policy"); return this; }
        /** Sets an exact numeric range.
         * @param range range and step
         * @return this builder
         */
        public Builder<T> range(MetricRange range) { this.range = Objects.requireNonNull(range, "range"); return this; }
        /** Sets a continuous range.
         * @param min inclusive minimum
         * @param max inclusive maximum
         * @return this builder
         */
        public Builder<T> range(double min, double max) { return range(min, max, 0); }
        /** Sets a stepped range.
         * @param min inclusive minimum
         * @param max inclusive maximum
         * @param step increment, zero for continuous
         * @return this builder
         */
        public Builder<T> range(double min, double max, double step) { return range(new MetricRange(min, max, step)); }
        /** Validates metadata and initial value.
         * @return new metric
         */
        public DeviceMetric<T> build() { return new DeviceMetric<>(this); }
    }
}
