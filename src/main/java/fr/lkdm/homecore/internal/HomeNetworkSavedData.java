package fr.lkdm.homecore.internal;

import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.network.NetworkRole;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;

/** Server-thread storage of logical networks in the overworld data directory. */
public final class HomeNetworkSavedData extends SavedData {
    public static final String FILE_ID = "homecore_networks";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENTRIES = 100_000;
    private static final Factory<HomeNetworkSavedData> FACTORY = new Factory<>(HomeNetworkSavedData::new, (tag, provider) -> {
        try {
            return load(tag, provider);
        } catch (RuntimeException failure) {
            // DimensionDataStorage swallows decoder exceptions and recreates data.
            // Retain a failed entry in its cache to prevent accidental replacement.
            return new HomeNetworkSavedData(failure);
        }
    });
    private final HomeNetworkManager manager;
    private final RuntimeException loadFailure;

    public HomeNetworkSavedData() {
        manager = new HomeNetworkManager(this::setDirty);
        loadFailure = null;
    }

    private HomeNetworkSavedData(List<HomeNetwork> networks) {
        manager = new HomeNetworkManager(networks, this::setDirty);
        loadFailure = null;
    }

    private HomeNetworkSavedData(RuntimeException failure) {
        manager = null;
        loadFailure = failure;
    }

    public static HomeNetworkSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Networks must be accessed on the server thread");
        DimensionDataStorage storage = server.overworld().getDataStorage();
        HomeNetworkSavedData existing = storage.get(FACTORY, FILE_ID);
        if (existing == null && Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_ID + ".dat"))) {
            throw new IllegalStateException("Existing HomeCore network data could not be read; refusing to replace it");
        }
        return get(storage);
    }

    /** Internal storage adapter, also used by persistence integration tests. */
    public static HomeNetworkSavedData get(DimensionDataStorage storage) {
        HomeNetworkSavedData data = storage.computeIfAbsent(FACTORY, FILE_ID);
        data.ensureValid();
        return data;
    }

    public HomeNetworkManager manager() {
        ensureValid();
        return manager;
    }

    private void ensureValid() {
        if (loadFailure != null) throw new IllegalStateException("HomeCore network data is invalid; refusing to replace it", loadFailure);
    }

    public static HomeNetworkSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        require(tag, "formatVersion", Tag.TAG_INT);
        if (tag.getInt("formatVersion") != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported HomeCore network data version");
        ListTag entries = list(tag, "networks");
        List<HomeNetwork> networks = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (Tag entry : entries) {
            CompoundTag network = (CompoundTag) entry;
            UUID id = uuid(network, "id");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate network UUID");
            require(network, "name", Tag.TAG_STRING);
            UUID owner = uuid(network, "owner");
            require(network, "createdAtSeconds", Tag.TAG_LONG);
            require(network, "createdAtNanos", Tag.TAG_INT);
            int nanos = network.getInt("createdAtNanos");
            if (nanos < 0 || nanos > 999_999_999) throw new IllegalArgumentException("Invalid timestamp nanoseconds");
            Instant created = Instant.ofEpochSecond(network.getLong("createdAtSeconds"), nanos);
            Map<UUID, NetworkRole> members = new HashMap<>();
            for (Tag memberEntry : list(network, "members")) {
                CompoundTag member = (CompoundTag) memberEntry;
                require(member, "role", Tag.TAG_STRING);
                if (members.putIfAbsent(uuid(member, "id"), NetworkRole.valueOf(member.getString("role"))) != null) {
                    throw new IllegalArgumentException("Duplicate network member");
                }
            }
            if (members.get(owner) != NetworkRole.OWNER) throw new IllegalArgumentException("Missing network owner role");
            Set<UUID> devices = new HashSet<>();
            for (Tag deviceEntry : list(network, "devices")) {
                if (!devices.add(uuid((CompoundTag) deviceEntry, "id"))) throw new IllegalArgumentException("Duplicate network device");
            }
            networks.add(new HomeNetwork(id, network.getString("name"), owner, members, devices, created));
        }
        return new HomeNetworkSavedData(networks);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ensureValid();
        tag.putInt("formatVersion", FORMAT_VERSION);
        ListTag networks = new ListTag();
        for (HomeNetwork network : manager.getAll()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", network.id());
            entry.putString("name", network.name());
            entry.putUUID("owner", network.owner());
            entry.putLong("createdAtSeconds", network.createdAt().getEpochSecond());
            entry.putInt("createdAtNanos", network.createdAt().getNano());
            ListTag members = new ListTag();
            network.members().forEach((id, role) -> {
                CompoundTag member = new CompoundTag();
                member.putUUID("id", id);
                member.putString("role", role.name());
                members.add(member);
            });
            entry.put("members", members);
            ListTag devices = new ListTag();
            for (UUID id : network.devices()) {
                CompoundTag device = new CompoundTag();
                device.putUUID("id", id);
                devices.add(device);
            }
            entry.put("devices", devices);
            networks.add(entry);
        }
        tag.put("networks", networks);
        return tag;
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) throw new IllegalArgumentException("Missing or invalid network data field: " + key);
    }

    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.hasUUID(key)) throw new IllegalArgumentException("Missing or invalid UUID: " + key);
        return tag.getUUID(key);
    }

    private static ListTag list(CompoundTag tag, String key) {
        require(tag, key, Tag.TAG_LIST);
        ListTag result = (ListTag) tag.get(key);
        if (result.size() > MAX_ENTRIES || !result.isEmpty() && result.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Invalid network data list: " + key);
        }
        return result;
    }
}
