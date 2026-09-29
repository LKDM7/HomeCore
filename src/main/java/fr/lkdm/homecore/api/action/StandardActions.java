package fr.lkdm.homecore.api.action;

import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.security.Permission;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Actions HomeCore derives from the optional {@link Switchable} and {@link Renamable} contracts, so every
 * dashboard offers the same power switch and rename field. A device declaring an action with the same
 * identifier keeps its own definition.
 */
public final class StandardActions {
    /** Toggle switching a {@link Switchable} device on or off; requires CONTROL. */
    public static final ResourceLocation POWER = ResourceLocation.fromNamespaceAndPath("homecore", "power");
    /** Text action renaming a {@link Renamable} device; requires CONFIGURE. */
    public static final ResourceLocation RENAME = ResourceLocation.fromNamespaceAndPath("homecore", "rename");

    private StandardActions() { }

    /** Tests whether an identifier belongs to a standard action.
     * @param id action identifier
     * @return whether HomeCore defines it
     */
    public static boolean isStandard(ResourceLocation id) { return POWER.equals(id) || RENAME.equals(id); }

    /** Builds the standard actions a device supports.
     * @param device source device
     * @return actions in display order, possibly empty
     */
    public static List<DeviceAction<?>> of(DashboardDevice device) {
        List<DeviceAction<?>> actions = new ArrayList<>(2);
        if (device instanceof Switchable switchable) actions.add(DeviceAction.toggle(POWER,
                        Component.translatableWithFallback("action.homecore.power", "Power"))
                .description(Component.translatableWithFallback("action.homecore.power.description", "Switch the machine on or off."))
                .handler((context, on) -> switchable.setPowered(on)).build());
        if (device instanceof Renamable renamable) actions.add(DeviceAction.builder(RENAME,
                        Component.translatableWithFallback("action.homecore.rename", "Rename"), ActionType.TEXT, String.class)
                .description(Component.translatableWithFallback("action.homecore.rename.description",
                        "New name for the machine; leave empty to restore its default name."))
                .maxLength(Renamable.MAX_LENGTH)
                .requiredPermission(Permission.CONFIGURE.id())
                .validator(StandardActions::validName)
                .handler((context, name) -> renamable.rename(name.strip())).build());
        return actions;
    }

    /** Accepts names an anvil could produce: bounded, without control or formatting characters.
     * @param name candidate
     * @return whether the name is acceptable
     */
    public static boolean validName(String name) {
        String value = name.strip();
        return value.length() <= Renamable.MAX_LENGTH
                && value.chars().noneMatch(character -> Character.isISOControl(character) || character == '§');
    }
}
