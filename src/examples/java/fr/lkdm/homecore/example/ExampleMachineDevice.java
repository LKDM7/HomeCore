package fr.lkdm.homecore.example;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.capability.CapabilitySet;
import fr.lkdm.homecore.api.capability.DeviceCapability;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.event.DeviceEventBus;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.Energy;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Complete consumer-side integration using only HomeCore's public API. The source
 * can be a logical machine or a block entity implementing {@link MachineState}.
 * Call {@link #refresh()} from that machine's own server lifecycle after changes;
 * HomeCore does not scan or tick the example automatically.
 */
public final class ExampleMachineDevice implements DashboardDevice {
    /** Public example capability; a consumer queries it without implementation casts. */
    public static final DeviceCapability<MachineInfo> MACHINE_INFO = new DeviceCapability<>(id("machine_info"), MachineInfo.class);
    /** Event sent after an actual metric change. */
    public static final ResourceLocation TEST_EVENT = id("test_event");

    private final MachineState source;
    private final UUID identity;
    private final DeviceEventBus events;
    private final DeviceMetric<Double> temperature;
    private final DeviceMetric<Energy> energy;
    private final DeviceMetric<Boolean> enabled;
    private final DeviceMetric<Percentage> progress;
    private final DeviceMetric<Integer> counter;
    private final List<DeviceMetric<?>> metrics;
    private final List<DeviceAction<?>> actions;
    private final CapabilitySet capabilities;
    private final DeviceSchema schema;

    /**
     * Adapts a stable source on the owning server thread.
     * @param source machine with its own persisted device identity
     * @param events server-scoped public event bus
     */
    public ExampleMachineDevice(MachineState source, DeviceEventBus events) {
        this.source = Objects.requireNonNull(source, "source");
        this.identity = Objects.requireNonNull(source.persistentDeviceId(), "persistentDeviceId");
        this.events = Objects.requireNonNull(events, "events");
        temperature = DeviceMetric.builder(id("temperature"), Component.literal("Temperature"), MetricTypes.DOUBLE, source.temperature())
                .unit(Unit.CELSIUS).updatePolicy(UpdatePolicy.NORMAL).build();
        energy = DeviceMetric.builder(id("energy"), Component.literal("Energy"), MetricTypes.ENERGY, source.energy())
                .unit(Unit.FE).updatePolicy(UpdatePolicy.FAST).build();
        enabled = DeviceMetric.builder(id("enabled"), Component.literal("Enabled"), MetricTypes.BOOLEAN, source.enabled())
                .updatePolicy(UpdatePolicy.ON_CHANGE).build();
        progress = DeviceMetric.builder(id("progress"), Component.literal("Progress"), MetricTypes.PERCENTAGE, source.progress())
                .unit(Unit.PERCENT).range(0, 100).updatePolicy(UpdatePolicy.ON_CHANGE).build();
        counter = DeviceMetric.builder(id("counter"), Component.literal("Counter"), MetricTypes.INTEGER, source.counter())
                .range(0, Integer.MAX_VALUE, 1).updatePolicy(UpdatePolicy.ON_CHANGE).build();
        metrics = List.of(temperature, energy, enabled, progress, counter);
        actions = List.of(
                DeviceAction.button(id("toggle_enabled"), Component.literal("Toggle enabled"))
                        .description(Component.literal("Switch the machine on or off."))
                        .handler((context, parameter) -> { source.setEnabled(!source.enabled()); refresh(); return ActionResult.success(); }).build(),
                DeviceAction.slider(id("set_progress"), Component.literal("Set progress"), 0, 100)
                        .description(Component.literal("Set progress from 0 to 100 percent."))
                        .step(1).handler((context, value) -> { source.setProgress(new Percentage(value)); refresh(); return ActionResult.success(); }).build(),
                DeviceAction.button(id("reset_counter"), Component.literal("Reset counter"))
                        .description(Component.literal("Reset the operation counter to zero."))
                        .handler((context, parameter) -> { source.resetCounter(); refresh(); return ActionResult.success(); }).build());
        capabilities = CapabilitySet.builder().add(MACHINE_INFO, (MachineInfo) () -> "Example Machine").build();
        schema = DeviceSchema.from(this);
    }

    /**
     * Registers the optional capability contract once during the integrating mod's setup.
     * Consumers should use their own namespace when adapting this example.
     */
    public static void registerCapability() { DashboardAPI.capabilities().register(MACHINE_INFO); }

    /**
     * Registers a strongly typed adapter for an external mod's block entity.
     * Its implementation must persist {@link MachineState#persistentDeviceId()}
     * in its own NBT. Invoke discovery explicitly from that block's server onLoad,
     * and unregister the returned device ID from its removal/unload lifecycle.
     *
     * @param type the integrating mod's registered block entity type
     * @param <T> block entity implementing the example state contract
     */
    public static <T extends BlockEntity & MachineState> void registerProvider(BlockEntityType<T> type) {
        DashboardAPI.registerDeviceProvider(type, source -> {
            if (!(source.getLevel() instanceof ServerLevel level)) throw new IllegalStateException("Provider requires a loaded server-side machine");
            return new ExampleMachineDevice(source, DashboardAPI.events(level.getServer()));
        });
    }

    /**
     * Synchronizes source state into typed metrics and emits one event for changed values.
     * Invoke on the server thread after the machine changes; do not poll every world block.
     * @return whether any metric changed
     */
    public boolean refresh() {
        if (!isValid()) return false;
        boolean changed = temperature.setValue(source.temperature());
        changed |= energy.setValue(source.energy());
        changed |= enabled.setValue(source.enabled());
        changed |= progress.setValue(source.progress());
        changed |= counter.setValue(source.counter());
        if (changed) events.publish(new DeviceEvent(TEST_EVENT, identity, Instant.now(), DeviceEvent.Severity.INFO,
                Map.of("enabled", Boolean.toString(enabled.value()), "progress", Double.toString(progress.value().value()), "counter", Integer.toString(counter.value()))));
        return changed;
    }

    @Override public UUID id() { return identity; }
    @Override public ResourceLocation deviceType() { return id("example_machine"); }
    @Override public Component displayName() { return Component.literal("Debug Dashboard Device"); }
    @Override public DeviceStatus status() { return isValid() ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE; }
    @Override public boolean isValid() { return source.valid() && identity.equals(source.persistentDeviceId()) && (!(source instanceof BlockEntity block) || !block.isRemoved()); }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public List<DeviceAction<?>> actions() { return actions; }
    @Override public Set<ResourceLocation> eventTypes() { return Set.of(TEST_EVENT); }
    @Override public Set<ResourceLocation> capabilities() { return capabilities.ids(); }
    @Override public <T> Optional<T> capability(DeviceCapability<T> capability) { return capabilities.query(capability); }
    @Override public DeviceSchema schema() { return schema; }
    @Override public Optional<BlockPos> position() { return source instanceof BlockEntity block ? Optional.of(block.getBlockPos().immutable()) : Optional.empty(); }
    @Override public Optional<ResourceKey<Level>> dimension() { return source instanceof BlockEntity block && block.getLevel() != null ? Optional.of(block.getLevel().dimension()) : Optional.empty(); }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("homecore", path); }

    /** Read-only example capability, independent of any implementation package. */
    @FunctionalInterface
    public interface MachineInfo {
        /** Returns the source machine's model name.
         * @return model label
         */
        String model();
    }

    /**
     * Minimal source contract implemented by a real mod's machine or a logical device.
     * Persist the UUID and changed state in the owning mod; setters should mark a
     * backing block entity dirty. No HomeCore implementation classes are required.
     */
    public interface MachineState {
        /** Returns a persisted identity, never a newly generated ID on every call.
         * @return stable device identity
         */
        UUID persistentDeviceId();
        /** Returns temperature in Celsius.
         * @return finite temperature
         */
        double temperature();
        /** Returns stored FE and capacity.
         * @return immutable energy storage
         */
        Energy energy();
        /** Returns the on/off state.
         * @return current state
         */
        boolean enabled();
        /** Returns percentage complete.
         * @return progress
         */
        Percentage progress();
        /** Returns the nonnegative operation count.
         * @return count
         */
        int counter();
        /** Changes the source's on/off state.
         * @param value desired state
         */
        void setEnabled(boolean value);
        /** Changes the source's progress.
         * @param value desired percentage
         */
        void setProgress(Percentage value);
        /** Resets the source's operation count. */
        void resetCounter();
        /** Returns whether this source still exists.
         * @return source validity
         */
        default boolean valid() { return true; }
    }

    /** Complete in-memory source used by the development mod; excluded from release artifacts. */
    public static final class MemoryMachine implements MachineState {
        private final UUID identity;
        private boolean enabled = true;
        private Percentage progress = new Percentage(73);
        private int counter = 42;
        private boolean valid = true;

        /** Creates development values with a caller-owned stable identity.
         * @param identity stable device identity
         */
        public MemoryMachine(UUID identity) { this.identity = Objects.requireNonNull(identity); }
        @Override public UUID persistentDeviceId() { return identity; }
        @Override public double temperature() { return 22.5; }
        @Override public Energy energy() { return new Energy(4500, 10000); }
        @Override public boolean enabled() { return enabled; }
        @Override public Percentage progress() { return progress; }
        @Override public int counter() { return counter; }
        @Override public void setEnabled(boolean value) { enabled = value; }
        @Override public void setProgress(Percentage value) { progress = Objects.requireNonNull(value); }
        @Override public void resetCounter() { counter = 0; }
        @Override public boolean valid() { return valid; }
        /** Invalidates this source when its owning development server stops. */
        public void invalidate() { valid = false; }
    }
}
