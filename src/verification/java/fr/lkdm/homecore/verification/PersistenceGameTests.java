package fr.lkdm.homecore.verification;

import com.mojang.logging.LogUtils;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.network.NetworkRole;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run write and read in separate server processes against the same world. */
@GameTestHolder(HomeCoreValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PersistenceGameTests {
    private static final String NAME = "homecore-validation-persistent";
    private static final UUID OWNER = UUID.fromString("42ae0b49-7429-4b0b-a6fe-c567b083ccf9");
    private static final UUID MEMBER = UUID.fromString("18944a60-9419-40ad-a039-c47938b279c2");
    private static final UUID DEVICE = UUID.fromString("896a1874-19f9-4e11-9a56-8bce126d3f17");

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void networkSurvivesRestart(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        HomeNetworkManager manager = DashboardAPI.networks(server);
        String pass = System.getProperty("homecore.persistencePass", "");
        if (pass.equals("write")) {
            manager.getAll().stream().filter(network -> network.name().equals(NAME))
                    .map(HomeNetwork::id).toList().forEach(manager::deleteNetwork);
            HomeNetwork network = manager.createNetwork(NAME, OWNER);
            manager.addDevice(network.id(), DEVICE);
            manager.setMember(network.id(), MEMBER, NetworkRole.MEMBER);
            server.saveEverything(false, true, true);
            LogUtils.getLogger().info("HOMECORE_PERSISTENCE_WRITE_OK network={} device={}", network.id(), DEVICE);
        } else if (pass.equals("read")) {
            var matches = manager.getAll().stream().filter(network -> network.name().equals(NAME)).toList();
            helper.assertTrue(matches.size() == 1, "Expected the network saved by the previous server process");
            HomeNetwork network = matches.getFirst();
            helper.assertTrue(network.owner().equals(OWNER), "Owner was not persisted");
            helper.assertTrue(network.members().get(OWNER) == NetworkRole.OWNER, "Owner role was not persisted");
            helper.assertTrue(network.members().get(MEMBER) == NetworkRole.MEMBER, "Member role was not persisted");
            helper.assertTrue(network.devices().contains(DEVICE), "Device membership was not persisted");
            helper.assertTrue(manager.getNetworksForPlayer(MEMBER).contains(network), "Membership query failed after restart");
            LogUtils.getLogger().info("HOMECORE_PERSISTENCE_READ_OK network={} device={}", network.id(), DEVICE);
        } else {
            helper.fail("Set homecore.persistencePass to write or read");
            return;
        }
        helper.succeed();
    }
}
