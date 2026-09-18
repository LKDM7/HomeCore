package fr.lkdm.homecore.api.registry;

import fr.lkdm.homecore.api.device.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceProviderRegistryTest {
    @Test void typedRegistrationDiscoveryAndRemoval() {
        var registry = new DeviceProviderRegistry();
        var chest = new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        var id = UUID.randomUUID();
        DeviceProvider<ChestBlockEntity> provider = source -> new DashboardDevice() {
            public UUID id() { return id; }
            public ResourceLocation deviceType() { return ResourceLocation.parse("homecore:chest"); }
            public Component displayName() { return Component.literal("Chest"); }
            public DeviceStatus status() { return DeviceStatus.ONLINE; }
        };
        assertTrue(registry.discover(chest).isEmpty());
        registry.register(BlockEntityType.CHEST, provider);
        assertEquals(id, registry.discover(chest).orElseThrow().id());
        assertThrows(IllegalArgumentException.class, () -> registry.register(BlockEntityType.CHEST, provider));
        assertTrue(registry.unregister(BlockEntityType.CHEST));
        registry.register(BlockEntityType.CHEST, ChestBlockEntity.class, provider);
        chest.setRemoved();
        assertTrue(registry.discover(chest).isEmpty());
    }
}
