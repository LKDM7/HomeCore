package fr.lkdm.homecore.api.action;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable typed description and handler of a device command.
 * Execution validates parameters but does not grant authorization: callers must first
 * perform server-side identity, permission, availability and rate-limit checks.
 * Instances do not schedule their handlers; callers must use the appropriate server thread.
 *
     * @param <T> parameter type
 */
public final class DeviceAction<T> {
    private final Builder<T> builder;

    private DeviceAction(Builder<T> source) {
        source.checkDefinition();
        builder = new Builder<>(source.id, source.displayName, source.type, source.valueType);
        builder.description = source.description.copy();
        builder.min = source.min;
        builder.max = source.max;
        builder.step = source.step;
        builder.maxLength = source.maxLength;
        builder.options = List.copyOf(source.options);
        builder.requiredPermission = source.requiredPermission;
        builder.validator = source.validator;
        builder.handler = source.handler;
    }

    /** Returns unique action identifier within the device.
     * @return unique action identifier within the device */
    public ResourceLocation id() { return builder.id; }
    /** Returns display label.
     * @return display label */
    public Component displayName() { return builder.displayName.copy(); }
    /** Returns description.
     * @return description */
    public Component description() { return builder.description.copy(); }
    /** Returns suggested control type.
     * @return suggested control type */
    public ActionType type() { return builder.type; }
    /** Returns runtime parameter type.
     * @return runtime parameter type */
    public Class<T> valueType() { return builder.valueType; }
    /** Returns inclusive lower limit, if present.
     * @return inclusive lower limit, if present */
    public OptionalDouble min() { return optional(builder.min); }
    /** Returns inclusive upper limit, if present.
     * @return inclusive upper limit, if present */
    public OptionalDouble max() { return optional(builder.max); }
    /** Returns increment relative to min, or zero when no min exists.
     * @return increment relative to min, or zero when no min exists */
    public OptionalDouble step() { return optional(builder.step); }
    /** Returns immutable selection options.
     * @return immutable selection options */
    public List<T> options() { return builder.options; }
    /** Returns maximum text length in UTF-16 code units.
     * @return maximum text length in UTF-16 code units */
    public int maxLength() { return builder.maxLength; }
    /** Returns permission identifier required by this action.
     * @return permission identifier required by this action */
    public ResourceLocation requiredPermission() { return builder.requiredPermission; }
    private static OptionalDouble optional(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /** Safely validates untrusted parameter values without invoking the handler.
     *
     * @param value candidate parameter
     *
     * @return SUCCESS or INVALID_PARAMETER
     */
    public ActionResult validate(Object value) {
        if (!builder.valueType.isInstance(value)) return invalid();
        return validateCanonical(canonicalValue(value));
    }

    private T canonicalValue(Object value) {
        return builder.valueType.cast(value instanceof BlockPos position ? position.immutable() : value);
    }

    private ActionResult validateCanonical(T typed) {
        if (typed instanceof Number number) {
            double numberValue = number.doubleValue();
            if (!Double.isFinite(numberValue)
                    || builder.min != null && numberValue < builder.min
                    || builder.max != null && numberValue > builder.max) return invalid();
            if (builder.step != null) {
                BigDecimal offset = BigDecimal.valueOf(numberValue)
                        .subtract(BigDecimal.valueOf(builder.min == null ? 0 : builder.min));
                if (offset.remainder(BigDecimal.valueOf(builder.step)).signum() != 0) return invalid();
            }
        }
        if (typed instanceof String text && text.length() > builder.maxLength) return invalid();
        if (builder.type == ActionType.SELECT && !builder.options.contains(typed)) return invalid();
        try {
            return builder.validator.test(typed) ? ActionResult.success() : invalid();
        } catch (RuntimeException exception) {
            return invalid();
        }
    }

    /** Validates then invokes a handler; permission checks remain the caller's responsibility.
     *
     * @param context trusted invocation identity
     *
     * @param value candidate parameter
     *
     * @return validation error, handler result, or FAILED if the handler throws
     */
    public ActionResult execute(ActionContext context, Object value) {
        Objects.requireNonNull(context, "context");
        if (!builder.valueType.isInstance(value)) return invalid();
        T canonical = canonicalValue(value);
        ActionResult validation = validateCanonical(canonical);
        if (!validation.isSuccess()) return validation;
        try {
            ActionResult result = builder.handler.apply(context, canonical);
            return result == null ? ActionResult.of(ActionResult.Code.FAILED) : result;
        } catch (RuntimeException exception) {
            return ActionResult.of(ActionResult.Code.FAILED);
        }
    }

    private static ActionResult invalid() { return ActionResult.of(ActionResult.Code.INVALID_PARAMETER); }

    /** Creates a typed action builder.
     *
     * @param id action identifier
     * @param name label
     * @param type control kind
     *
     * @param valueType runtime parameter type
     * @param <T> parameter type
     * @return builder
     */
    public static <T> Builder<T> builder(ResourceLocation id, Component name, ActionType type, Class<T> valueType) {
        return new Builder<>(id, name, type, valueType);
    }

    /** Creates a button with the explicit {@link Unit#INSTANCE} parameter.
     * @param id action identifier
     * @param name label
     * @return button builder
     */
    public static Builder<Unit> button(ResourceLocation id, Component name) {
        return builder(id, name, ActionType.BUTTON, Unit.class);
    }

    /** Creates a boolean switch.
     * @param id action identifier
     * @param name label
     * @return toggle builder
     */
    public static Builder<Boolean> toggle(ResourceLocation id, Component name) {
        return builder(id, name, ActionType.TOGGLE, Boolean.class);
    }

    /** Creates a bounded slider.
     * @param id action identifier
     * @param name label
     * @param min inclusive minimum
     * @param max inclusive maximum
     * @return slider builder
     */
    public static Builder<Double> slider(ResourceLocation id, Component name, double min, double max) {
        return builder(id, name, ActionType.SLIDER, Double.class).range(min, max);
    }

    /** Mutable configuration, copied when built.
     * @param <T> parameter type */
    public static final class Builder<T> {
        private final ResourceLocation id;
        private final Component displayName;
        private final ActionType type;
        private final Class<T> valueType;
        private Component description = Component.empty();
        private Double min;
        private Double max;
        private Double step;
        private int maxLength = 1024;
        private List<T> options = List.of();
        private ResourceLocation requiredPermission = ResourceLocation.fromNamespaceAndPath("homecore", "control");
        private Predicate<T> validator = value -> true;
        private BiFunction<ActionContext, T, ActionResult> handler;

        private Builder(ResourceLocation id, Component name, ActionType type, Class<T> valueType) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = Objects.requireNonNull(name, "name").copy();
            this.type = Objects.requireNonNull(type, "type");
            this.valueType = Objects.requireNonNull(valueType, "valueType");
        }
        /** Sets the user-facing explanation.
     * @param value explanation
     * @return this builder */
        public Builder<T> description(Component value) { description = Objects.requireNonNull(value).copy(); return this; }
        /** Sets inclusive numeric bounds.
     * @param minimum inclusive minimum
     * @param maximum inclusive maximum
     * @return this builder */
        public Builder<T> range(double minimum, double maximum) { min = minimum; max = maximum; return this; }
        /** Sets a strictly positive increment relative to the minimum, or relative to zero when unbounded.
     * @param value positive increment from the minimum or zero
     * @return this builder */
        public Builder<T> step(double value) { step = value; return this; }
        /** Sets the allowed immutable SELECT values.
     * @param values nonempty valid selection values
     * @return this builder */
        public Builder<T> options(List<T> values) { options = List.copyOf(values); return this; }
        /** Sets the local text limit; network transport caps text at 4096 characters.
     * @param value maximum UTF-16 text length
     * @return this builder */
        public Builder<T> maxLength(int value) { maxLength = value; return this; }
        /** Sets the additional permission checked alongside CONTROL.
     * @param value required permission id
     * @return this builder */
        public Builder<T> requiredPermission(ResourceLocation value) { requiredPermission = Objects.requireNonNull(value); return this; }
        /** Sets a side-effect-free parameter validator.
     * @param value additional pure validation predicate
     * @return this builder */
        public Builder<T> validator(Predicate<T> value) { validator = Objects.requireNonNull(value); return this; }
        /** Sets the trusted server handler.
     * @param value command implementation
     * @return this builder */
        public Builder<T> handler(BiFunction<ActionContext,T,ActionResult> value) { handler = Objects.requireNonNull(value); return this; }
        /** Returns validated immutable action.
     * @return validated immutable action
     * @throws IllegalArgumentException invalid definition */
        public DeviceAction<T> build() { return new DeviceAction<>(this); }

        private void checkDefinition() {
            Class<?> expected = switch (type) {
                case BUTTON -> Unit.class;
                case TOGGLE -> Boolean.class;
                case INTEGER -> Integer.class;
                case DOUBLE, SLIDER -> Double.class;
                case TEXT -> String.class;
                case POSITION -> BlockPos.class;
                case SELECT -> valueType;
            };
            if (valueType != expected || valueType.isPrimitive()) throw new IllegalArgumentException("Parameter type does not match action type");
            boolean numeric = type == ActionType.INTEGER || type == ActionType.DOUBLE || type == ActionType.SLIDER;
            if (!numeric && (min != null || max != null || step != null)) throw new IllegalArgumentException("Range/step require a numeric action");
            if (min != null && (!Double.isFinite(min) || !Double.isFinite(max) || min > max)) throw new IllegalArgumentException("Invalid range");
            if (step != null && (!Double.isFinite(step) || step <= 0)) throw new IllegalArgumentException("Invalid step");
            if (type == ActionType.INTEGER && ((min != null && (min != Math.rint(min) || max != Math.rint(max)
                    || min < Integer.MIN_VALUE || max > Integer.MAX_VALUE)) || step != null && step != Math.rint(step))) throw new IllegalArgumentException("Integer range/step must be integral and representable");
            if (type == ActionType.SLIDER && min == null) throw new IllegalArgumentException("Slider requires bounds");
            if (maxLength < 0 || maxLength > 32767) throw new IllegalArgumentException("Text length must be between 0 and 32767");
            if (type == ActionType.SELECT && !(valueType.isEnum() || valueType == String.class
                    || valueType == Integer.class || valueType == Boolean.class || valueType == Double.class)) {
                throw new IllegalArgumentException("Selections require immutable scalar or enum values");
            }
            if (type == ActionType.SELECT && (options.isEmpty() || options.size() > 256 || options.stream().anyMatch(value -> !valueType.isInstance(value)
                    || value instanceof String text && text.length() > maxLength
                    || value instanceof Double number && !Double.isFinite(number))
                    || options.stream().distinct().count() != options.size())) throw new IllegalArgumentException("Invalid selection options");
            if (type != ActionType.SELECT && !options.isEmpty()) throw new IllegalArgumentException("Options require SELECT");
            if (handler == null) throw new IllegalArgumentException("An action requires a handler");
        }
    }
}
