package fr.lkdm.homecore.api.security;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.ActionType;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.action.StandardActions;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StandardActionsTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final DeviceRegistry devices = new DeviceRegistry();
    private final HomeNetworkManager networks = new HomeNetworkManager();
    private final HomeNetwork home = networks.createNetwork("Home", owner);
    private final Machine machine = new Machine();
    private final ActionExecutor executor = new ActionExecutor(devices, networks, new PermissionValidator(),
            new RateLimiter(100, Duration.ofSeconds(1), 100, () -> 0));

    StandardActionsTest() {
        devices.register(machine);
        networks.addDevice(home.id(), machine.id());
        networks.setMember(home.id(), member, NetworkRole.MEMBER);
    }

    @Test void optionalContractsAddPowerToggleAndRenameAfterDeclaredActions() {
        var actions = DeviceSchema.from(machine).actions();
        assertEquals(List.of(ResourceLocation.parse("test:own"), StandardActions.POWER, StandardActions.RENAME),
                actions.stream().map(DeviceAction::id).toList());
        assertEquals(ActionType.TOGGLE, actions.get(1).type());
        assertEquals(ActionType.TEXT, actions.get(2).type());
        assertEquals(Renamable.MAX_LENGTH, actions.get(2).maxLength());
        assertEquals(Permission.CONFIGURE.id(), actions.get(2).requiredPermission());
    }

    @Test void declaredStandardIdentifierWinsOverTheGeneratedAction() {
        machine.overridePower = true;
        var actions = DeviceSchema.from(machine).actions();
        assertEquals(1, actions.stream().filter(action -> action.id().equals(StandardActions.POWER)).count());
        assertEquals(ActionType.BUTTON, actions.stream().filter(action -> action.id().equals(StandardActions.POWER))
                .findFirst().orElseThrow().type());
    }

    @Test void powerAndRenameWorkWhileTheMachineIsOffButNotWhenUnloaded() {
        machine.status = DeviceStatus.DISABLED;
        assertEquals(ActionResult.Code.SUCCESS, run(owner, StandardActions.POWER, true));
        assertTrue(machine.powered);
        assertEquals(ActionResult.Code.SUCCESS, run(owner, StandardActions.RENAME, "  Kitchen battery  "));
        assertEquals("Kitchen battery", machine.name);
        assertEquals(ActionResult.Code.DEVICE_OFFLINE, run(owner, ResourceLocation.parse("test:own"), fr.lkdm.homecore.api.action.Unit.INSTANCE));
        machine.status = DeviceStatus.OFFLINE;
        assertEquals(ActionResult.Code.DEVICE_OFFLINE, run(owner, StandardActions.POWER, false));
        assertTrue(machine.powered);
    }

    @Test void renameNeedsConfigureAndRejectsFormattingAndOverlongNames() {
        assertEquals(ActionResult.Code.SUCCESS, run(member, StandardActions.POWER, true));
        assertEquals(ActionResult.Code.DENIED, run(member, StandardActions.RENAME, "Mine"));
        assertEquals(ActionResult.Code.INVALID_PARAMETER, run(owner, StandardActions.RENAME, "§cRed"));
        assertEquals(ActionResult.Code.INVALID_PARAMETER, run(owner, StandardActions.RENAME, "a\nb"));
        assertEquals(ActionResult.Code.INVALID_PARAMETER, run(owner, StandardActions.RENAME, "x".repeat(Renamable.MAX_LENGTH + 1)));
        assertEquals(ActionResult.Code.SUCCESS, run(owner, StandardActions.RENAME, ""));
        assertEquals("", machine.name);
    }

    private ActionResult.Code run(UUID player, ResourceLocation action, Object value) {
        return executor.execute(player, home.id(), machine.id(), action, value).code();
    }

    private static final class Machine implements DashboardDevice, Switchable, Renamable {
        private final UUID id = UUID.randomUUID();
        private DeviceStatus status = DeviceStatus.ONLINE;
        private boolean powered;
        private boolean overridePower;
        private String name = "Machine";
        public UUID id() { return id; }
        public ResourceLocation deviceType() { return ResourceLocation.parse("test:machine"); }
        public Component displayName() { return Component.literal(name); }
        public DeviceStatus status() { return status; }
        public List<DeviceAction<?>> actions() {
            var own = DeviceAction.button(ResourceLocation.parse("test:own"), Component.literal("Own"))
                    .handler((context, value) -> ActionResult.success()).build();
            if (!overridePower) return List.of(own);
            return List.of(own, DeviceAction.button(StandardActions.POWER, Component.literal("Custom"))
                    .handler((context, value) -> ActionResult.success()).build());
        }
        public boolean powered() { return powered; }
        public ActionResult setPowered(boolean value) { powered = value; return ActionResult.success(); }
        public ActionResult rename(String value) { name = value; return ActionResult.success(); }
    }
}
