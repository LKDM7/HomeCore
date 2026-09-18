package fr.lkdm.homecore.api.action;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ActionTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "action");
    private static final ActionContext CONTEXT = new ActionContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test void validatesBoundsAndStepBeforeCallingHandler() {
        AtomicInteger calls = new AtomicInteger();
        var action = DeviceAction.builder(ID, Component.literal("Progress"), ActionType.SLIDER, Double.class)
                .range(0, 100).step(0.5).handler((context, value) -> {
                    calls.incrementAndGet(); return ActionResult.success();
                }).build();
        for (Object value : List.of(-0.5, 100.5, 50.25, Double.NaN, Double.POSITIVE_INFINITY, 50, "50")) {
            assertEquals(ActionResult.Code.INVALID_PARAMETER, action.execute(CONTEXT, value).code());
        }
        assertEquals(0, calls.get());
        assertTrue(action.execute(CONTEXT, 0.0).isSuccess());
        assertTrue(action.execute(CONTEXT, 100.0).isSuccess());
        assertEquals(2, calls.get());
    }

    @Test void validatesSelectionTextAndNull() {
        var select = DeviceAction.builder(ID, Component.empty(), ActionType.SELECT, String.class)
                .options(List.of("eco", "fast")).handler((context, value) -> ActionResult.success()).build();
        assertTrue(select.validate("eco").isSuccess());
        assertFalse(select.validate("unknown").isSuccess());
        assertFalse(select.validate(null).isSuccess());
        var text = DeviceAction.builder(ID, Component.empty(), ActionType.TEXT, String.class)
                .maxLength(3).validator(value -> !value.isBlank())
                .handler((context, value) -> ActionResult.success()).build();
        assertTrue(text.validate("abc").isSuccess());
        assertFalse(text.validate("abcd").isSuccess());
        assertFalse(text.validate("  ").isSuccess());
    }

    @Test void rejectsInvalidDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> decimal().range(10, 1).build());
        assertThrows(IllegalArgumentException.class, () -> decimal().range(0, Double.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class, () -> decimal().step(0).build());
        assertThrows(IllegalArgumentException.class, () -> decimal().step(Double.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> DeviceAction.builder(ID, Component.empty(), ActionType.TOGGLE, String.class)
                .handler((context, value) -> ActionResult.success()).build());
        assertThrows(IllegalArgumentException.class, () -> DeviceAction.builder(ID, Component.empty(), ActionType.SELECT, String.class)
                .handler((context, value) -> ActionResult.success()).build());
    }

    @Test void convertsHandlerExceptionsAndNullResultsToFailure() {
        assertEquals(ActionResult.Code.FAILED, decimal().handler((context, value) -> {
            throw new IllegalStateException("test");
        }).build().execute(CONTEXT, 1.0).code());
        assertEquals(ActionResult.Code.FAILED, decimal().handler((context, value) -> null).build().execute(CONTEXT, 1.0).code());
    }

    @Test void definitionIsUnaffectedBySubsequentBuilderChanges() {
        var builder = decimal().range(0, 100);
        var action = builder.build();
        builder.range(0, 10);
        assertTrue(action.validate(50.0).isSuccess());
    }

    @Test void positionIsCopiedBeforeValidationAndHandlerUsesThatSameCopy() {
        var supplied = new BlockPos.MutableBlockPos(1, 2, 3);
        var validated = new AtomicReference<BlockPos>();
        var handled = new AtomicReference<BlockPos>();
        var action = DeviceAction.builder(ID, Component.empty(), ActionType.POSITION, BlockPos.class)
                .validator(position -> {
                    validated.set(position);
                    supplied.set(10, 20, 30);
                    return position.equals(new BlockPos(1, 2, 3));
                }).handler((context, position) -> {
                    handled.set(position);
                    return ActionResult.success();
                }).build();
        assertTrue(action.execute(CONTEXT, supplied).isSuccess());
        assertSame(validated.get(), handled.get());
        assertFalse(handled.get() instanceof BlockPos.MutableBlockPos);
        assertEquals(new BlockPos(1, 2, 3), handled.get());
        supplied.set(100, 200, 300);
        assertEquals(new BlockPos(1, 2, 3), handled.get());
    }

    private DeviceAction.Builder<Double> decimal() {
        return DeviceAction.builder(ID, Component.empty(), ActionType.DOUBLE, Double.class)
                .handler((context, value) -> ActionResult.success());
    }
}
