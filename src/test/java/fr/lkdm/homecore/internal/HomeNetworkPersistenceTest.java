package fr.lkdm.homecore.internal;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homecore.api.network.NetworkRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HomeNetworkPersistenceTest {
    @TempDir Path directory;

    @Test void networksSurviveDiskSaveNewStorageAndSecondRestart() {
        var firstStorage = storage();
        var first = HomeNetworkSavedData.get(firstStorage);
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        var network = first.manager().createNetwork("Home", owner);
        first.manager().setMember(network.id(), member, NetworkRole.VIEWER);
        first.manager().addDevice(network.id(), device);
        first.manager().renameNetwork(network.id(), "Base principale");
        var expected = first.manager().getNetwork(network.id()).orElseThrow();
        assertTrue(first.isDirty());
        save(firstStorage);
        assertFalse(first.isDirty());
        assertTrue(Files.exists(directory.resolve(HomeNetworkSavedData.FILE_ID + ".dat")));

        var secondStorage = storage();
        var second = HomeNetworkSavedData.get(secondStorage);
        assertNotSame(first, second);
        assertFalse(second.isDirty());
        assertEquals(expected, second.manager().getNetwork(network.id()).orElseThrow());
        assertEquals(1, second.manager().getNetworksForPlayer(member).size());
        second.manager().removeDevice(network.id(), device);
        second.manager().setMember(network.id(), member, NetworkRole.ADMIN);
        second.manager().renameNetwork(network.id(), "Entrepôt");
        save(secondStorage);

        var third = HomeNetworkSavedData.get(storage());
        assertTrue(third.manager().getDevices(network.id()).isEmpty());
        assertEquals("Entrepôt", third.manager().getNetwork(network.id()).orElseThrow().name());
        assertEquals(NetworkRole.ADMIN, third.manager().getNetwork(network.id()).orElseThrow().members().get(member));
        assertEquals(network.createdAt(), third.manager().getNetwork(network.id()).orElseThrow().createdAt());
    }

    @Test void rejectsMalformedVersionDuplicateIdsAndInvalidOwner() {
        var data = new HomeNetworkSavedData();
        data.manager().createNetwork("Home", UUID.randomUUID());
        var valid = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        var badVersion = valid.copy();
        badVersion.putInt("formatVersion", 999);
        assertThrows(IllegalArgumentException.class, () -> HomeNetworkSavedData.load(badVersion, RegistryAccess.EMPTY));
        var duplicate = valid.copy();
        var entries = duplicate.getList("networks", Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> HomeNetworkSavedData.load(duplicate, RegistryAccess.EMPTY));
        var invalidOwner = valid.copy();
        invalidOwner.getList("networks", Tag.TAG_COMPOUND).getCompound(0).put("members", new ListTag());
        assertThrows(IllegalArgumentException.class, () -> HomeNetworkSavedData.load(invalidOwner, RegistryAccess.EMPTY));
        var missingList = valid.copy();
        missingList.remove("networks");
        assertThrows(IllegalArgumentException.class, () -> HomeNetworkSavedData.load(missingList, RegistryAccess.EMPTY));
    }

    @Test void unsupportedDiskVersionCannotBeRecreatedOrOverwrittenOnRetry() throws Exception {
        CompoundTag invalid = new CompoundTag();
        invalid.putInt("formatVersion", 999);
        invalid.put("networks", new ListTag());
        CompoundTag file = new CompoundTag();
        file.put("data", invalid);
        Path path = directory.resolve(HomeNetworkSavedData.FILE_ID + ".dat");
        NbtIo.writeCompressed(file, path);
        byte[] original = Files.readAllBytes(path);
        var storage = storage();
        assertThrows(IllegalStateException.class, () -> HomeNetworkSavedData.get(storage));
        assertThrows(IllegalStateException.class, () -> HomeNetworkSavedData.get(storage));
        save(storage);
        assertArrayEquals(original, Files.readAllBytes(path));
    }

    private DimensionDataStorage storage() {
        return new DimensionDataStorage(directory.toFile(), DataFixers.getDataFixer(), RegistryAccess.EMPTY);
    }

    private static void save(DimensionDataStorage storage) {
        storage.save();
        IOUtilities.waitUntilIOWorkerComplete();
    }
}
