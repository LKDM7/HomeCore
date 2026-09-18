package fr.lkdm.homecore.api.security;

import static org.junit.jupiter.api.Assertions.*;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.HomeNetworkManager;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ActionExecutorTest {
    private static final ResourceLocation ACTION = ResourceLocation.fromNamespaceAndPath("test", "progress");
    private final UUID owner = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID viewer = UUID.randomUUID();
    private final DeviceRegistry devices = new DeviceRegistry();
    private final HomeNetworkManager networks = new HomeNetworkManager();
    private final HomeNetwork home = networks.createNetwork("Home", owner);
    private final TestDevice device = new TestDevice();

    ActionExecutorTest() {
        devices.register(device);
        networks.addDevice(home.id(), device.id());
        networks.setMember(home.id(), member, NetworkRole.MEMBER);
        networks.setMember(home.id(), viewer, NetworkRole.VIEWER);
    }

    @Test void viewerDeniedMemberAllowedAndOwnerHasAllKnownPermissions() {
        var executor = executor(new PermissionValidator(), 100);
        assertEquals(ActionResult.Code.DENIED, invoke(executor, viewer, 50.0));
        assertEquals(ActionResult.Code.SUCCESS, invoke(executor, member, 50.0));
        assertEquals(ActionResult.Code.SUCCESS, invoke(executor, owner, 50.0));
        var policy = new PermissionValidator(Map.of());
        for (Permission permission : Permission.values()) assertTrue(policy.hasPermission(home, owner, permission));
        assertEquals(2, device.calls.get());
    }

    @Test void customPolicyAndRevokedMembershipAreRespectedImmediately() {
        var restricted = executor(new PermissionValidator(Map.of(NetworkRole.MEMBER, Set.of(Permission.VIEW))), 100);
        assertEquals(ActionResult.Code.DENIED, invoke(restricted, member, 50.0));
        assertEquals(ActionResult.Code.SUCCESS, invoke(restricted, owner, 50.0));
        var defaults = executor(new PermissionValidator(), 100);
        assertEquals(ActionResult.Code.SUCCESS, invoke(defaults, member, 50.0));
        networks.removeMember(home.id(), member);
        assertEquals(ActionResult.Code.DENIED, invoke(defaults, member, 50.0));
    }

    @Test void controlAndDeclaredPermissionAreBothRequiredUnknownPermissionDeniedEvenForOwner() {
        devices.unregister(device.id());
        device.permission = Permission.CONFIGURE.id();
        devices.register(device);
        var executor = executor(new PermissionValidator(), 100);
        assertEquals(ActionResult.Code.DENIED, invoke(executor, member, 50.0));
        assertEquals(ActionResult.Code.SUCCESS, invoke(executor, owner, 50.0));
        var configureOnly = executor(new PermissionValidator(Map.of(NetworkRole.MEMBER, Set.of(Permission.CONFIGURE))), 100);
        assertEquals(ActionResult.Code.DENIED, invoke(configureOnly, member, 50.0));
        devices.unregister(device.id());
        device.permission = Permission.VIEW.id();
        devices.register(device);
        assertEquals(ActionResult.Code.DENIED, invoke(executor, viewer, 50.0));
        devices.unregister(device.id());
        device.permission = ResourceLocation.fromNamespaceAndPath("unknown", "permission");
        devices.register(device);
        assertEquals(ActionResult.Code.DENIED, invoke(executor, owner, 50.0));
    }

    @Test void invalidValuesOfflineDevicesAndUnknownIdentifiersNeverExecute() {
        var executor = executor(new PermissionValidator(), 100);
        assertEquals(ActionResult.Code.INVALID_PARAMETER, invoke(executor, owner, 100.5));
        assertEquals(ActionResult.Code.INVALID_PARAMETER, invoke(executor, owner, Double.NaN));
        assertEquals(ActionResult.Code.INVALID_PARAMETER, invoke(executor, owner, "50"));
        device.status = DeviceStatus.OFFLINE;
        assertEquals(ActionResult.Code.DEVICE_OFFLINE, invoke(executor, owner, 50.0));
        device.status = DeviceStatus.ONLINE;
        assertEquals(ActionResult.Code.FAILED, executor.execute(owner, home.id(), UUID.randomUUID(), ACTION, 50.0).code());
        assertEquals(ActionResult.Code.DENIED, executor.execute(owner, UUID.randomUUID(), device.id(), ACTION, 50.0).code());
        assertEquals(ActionResult.Code.INVALID_PARAMETER, executor.execute(owner, home.id(), device.id(), ResourceLocation.parse("test:missing"), 50.0).code());
        assertEquals(0, device.calls.get());
    }

    @Test void networkMembershipDoesNotAuthorizeDevicesFromAnotherNetwork() {
        var executor = executor(new PermissionValidator(), 100);
        var other = networks.createNetwork("Other", owner);
        assertEquals(ActionResult.Code.DENIED, executor.execute(owner, other.id(), device.id(), ACTION, 50.0).code());
        assertEquals(ActionResult.Code.DENIED, invoke(executor, UUID.randomUUID(), 50.0));
        assertEquals(0, device.calls.get());
    }

    @Test void allAttemptsConsumeOneSharedPlayerBudgetAcrossDevices() {
        var executor = executor(new PermissionValidator(), 2);
        assertEquals(ActionResult.Code.DENIED, invoke(executor, viewer, 50.0));
        assertEquals(ActionResult.Code.FAILED, executor.execute(viewer, home.id(), UUID.randomUUID(), ACTION, 50.0).code());
        assertEquals(ActionResult.Code.RATE_LIMITED, invoke(executor, viewer, 50.0));
        assertEquals(ActionResult.Code.INVALID_PARAMETER, invoke(executor, member, -1.0));
        var other = new TestDevice();
        devices.register(other);
        networks.addDevice(home.id(), other.id());
        assertEquals(ActionResult.Code.SUCCESS, executor.execute(member, home.id(), other.id(), ACTION, 50.0).code());
        assertEquals(ActionResult.Code.RATE_LIMITED, invoke(executor, member, 50.0));
    }

    @Test void brokenIntegrationCallbacksAreContained() {
        var executor = executor(new PermissionValidator(), 100);
        device.throwValidity = true;
        assertEquals(ActionResult.Code.FAILED, invoke(executor, owner, 50.0));
        device.throwValidity = false;
        device.throwStatus = true;
        assertEquals(ActionResult.Code.FAILED, invoke(executor, owner, 50.0));
        device.throwStatus = false;
        device.throwHandler = true;
        assertEquals(ActionResult.Code.FAILED, invoke(executor, owner, 50.0));
    }

    @Test void transportResolverRunsOnlyAfterAuthorizationAndItsErrorsAreInvalidParameters() {
        var executor = executor(new PermissionValidator(), 100);
        AtomicInteger resolutions = new AtomicInteger();
        assertEquals(ActionResult.Code.DENIED, executor.executeResolved(viewer, home.id(), device.id(), ACTION, action -> {
            resolutions.incrementAndGet();
            return 50.0;
        }).code());
        assertEquals(0, resolutions.get());
        assertEquals(ActionResult.Code.INVALID_PARAMETER, executor.executeResolved(owner, home.id(), device.id(), ACTION, action -> {
            throw new IllegalArgumentException("bad wire value");
        }).code());
        assertEquals(0, device.calls.get());
    }

    private ActionExecutor executor(PermissionValidator policy, int capacity) {
        return new ActionExecutor(devices, networks, policy, new RateLimiter(capacity, Duration.ofSeconds(1), 100, () -> 0));
    }

    private ActionResult.Code invoke(ActionExecutor executor, UUID player, Object parameter) {
        return executor.execute(player, home.id(), device.id(), ACTION, parameter).code();
    }

    private static final class TestDevice implements DashboardDevice {
        private final UUID id = UUID.randomUUID();
        private final AtomicInteger calls = new AtomicInteger();
        private DeviceStatus status = DeviceStatus.ONLINE;
        private ResourceLocation permission = Permission.CONTROL.id();
        private boolean throwValidity;
        private boolean throwStatus;
        private boolean throwHandler;
        public UUID id() { return id; }
        public ResourceLocation deviceType() { return ResourceLocation.parse("test:machine"); }
        public Component displayName() { return Component.literal("Machine"); }
        public DeviceStatus status() {
            if (throwStatus) throw new IllegalStateException("broken status");
            return status;
        }
        public boolean isValid() {
            if (throwValidity) throw new IllegalStateException("broken integration");
            return true;
        }
        public List<DeviceAction<?>> actions() {
            return List.of(DeviceAction.slider(ACTION, Component.literal("Progress"), 0, 100)
                    .requiredPermission(permission).handler((context, value) -> {
                        if (throwHandler) throw new IllegalStateException("broken handler");
                        calls.incrementAndGet();
                        return ActionResult.success();
                    }).build());
        }
    }
}
