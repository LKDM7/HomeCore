package fr.lkdm.homecore.api.device;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceSchemaTest {
    @Test void debugMachineSchemaExposesTypedValuesAndCommands() {
        var temperature = fr.lkdm.homecore.api.metric.DeviceMetric.builder(
                ResourceLocation.parse("homecore:temperature"), Component.literal("Temperature"),
                fr.lkdm.homecore.api.metric.MetricTypes.DOUBLE, 22.5)
                .unit(fr.lkdm.homecore.api.metric.Unit.CELSIUS).build();
        var energy = fr.lkdm.homecore.api.metric.DeviceMetric.builder(
                ResourceLocation.parse("homecore:energy"), Component.literal("Energy"),
                fr.lkdm.homecore.api.metric.MetricTypes.LONG, 4500L)
                .unit(fr.lkdm.homecore.api.metric.Unit.FE).build();
        var progress = fr.lkdm.homecore.api.metric.DeviceMetric.builder(
                ResourceLocation.parse("homecore:progress"), Component.literal("Progress"),
                fr.lkdm.homecore.api.metric.MetricTypes.INTEGER, 73).range(0, 100).build();
        var enabled = fr.lkdm.homecore.api.metric.DeviceMetric.builder(
                ResourceLocation.parse("homecore:enabled"), Component.literal("Enabled"),
                fr.lkdm.homecore.api.metric.MetricTypes.BOOLEAN, true).build();
        var toggle = fr.lkdm.homecore.api.action.DeviceAction.builder(ResourceLocation.parse("homecore:toggle"),
                Component.literal("Toggle"), fr.lkdm.homecore.api.action.ActionType.TOGGLE, Boolean.class)
                .handler((context, value) -> { enabled.setValue(value); return fr.lkdm.homecore.api.action.ActionResult.success(); }).build();
        var setProgress = fr.lkdm.homecore.api.action.DeviceAction.builder(ResourceLocation.parse("homecore:set_progress"),
                Component.literal("Set progress"), fr.lkdm.homecore.api.action.ActionType.INTEGER, Integer.class)
                .range(0, 100).handler((context, value) -> { progress.setValue(value); return fr.lkdm.homecore.api.action.ActionResult.success(); }).build();
        var schema = new DeviceSchema(List.of(temperature, energy, progress, enabled), List.of(toggle, setProgress), java.util.Set.of());
        assertEquals(4, schema.metrics().size());
        assertEquals(2, schema.actions().size());
        assertEquals(22.5, temperature.value());
        assertEquals(4500L, energy.value());
        assertEquals(73, progress.value());
        assertTrue(enabled.value());
        assertThrows(IllegalArgumentException.class, () -> new DeviceSchema(List.of(temperature, temperature), List.of(), java.util.Set.of()));
    }

    @Test void logicalDeviceNeedsNoBlockEntityOrPosition() {
        DashboardDevice device = new DashboardDevice() {
            public UUID id() { return UUID.fromString("4a69b8a2-5f50-4722-a9be-bda39bccf084"); }
            public ResourceLocation deviceType() { return ResourceLocation.parse("homecore:test"); }
            public Component displayName() { return Component.literal("Debug Machine"); }
            public DeviceStatus status() { return DeviceStatus.ONLINE; }
        };
        assertTrue(device.position().isEmpty());
        assertTrue(device.dimension().isEmpty());
        assertEquals(List.of(), device.schema().metrics());
        assertEquals(List.of(), device.schema().actions());
        assertThrows(UnsupportedOperationException.class, () -> device.schema().metrics().add(null));
    }

    @Test void statusPreservesOptionalExplanationWithoutSharingMutableText() {
        var text = Component.literal("Waiting");
        var status = DeviceStatus.OFFLINE.withMessage(text);
        text.append(" changed");
        assertEquals("Waiting", status.message().orElseThrow().getString());
        assertEquals(DeviceStatus.State.OFFLINE, status.state());
    }
}
