package fr.lkdm.homecore.api.metric;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Runtime type contract for a metric. Values must be immutable.
 * @param <T> value type
 */
public final class MetricType<T> {
    private final ResourceLocation id;
    private final Class<T> valueClass;
    private final Predicate<T> validator;
    private final Function<T, BigDecimal> numericValue;

    /** Creates a nonnumeric type with an additional value validator.
     * @param id stable type identifier
     * @param valueClass runtime value class
     * @param validator accepted-value predicate
     */
    public MetricType(ResourceLocation id, Class<T> valueClass, Predicate<T> validator) {
        this(id, valueClass, validator, null);
    }

    /** Creates a type with an optional exact numeric projection for range validation.
     * @param id stable type identifier
     * @param valueClass runtime value class
     * @param validator accepted-value predicate
     * @param numericValue numeric projection, or null for nonnumeric values
     */
    public MetricType(ResourceLocation id, Class<T> valueClass, Predicate<T> validator, Function<T, BigDecimal> numericValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.valueClass = Objects.requireNonNull(valueClass, "valueClass");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.numericValue = numericValue;
    }

    /** Returns the stable type identifier.
     * @return type ID
     */
    public ResourceLocation id() { return id; }
    /** Returns the runtime value class.
     * @return value class
     */
    public Class<T> valueClass() { return valueClass; }
    /** Indicates whether this type supports ranges.
     * @return whether numeric
     */
    public boolean numeric() { return numericValue != null; }
    /** Validates null, runtime class and type constraints.
     * @param value candidate value
     * @return value cast to the checked type
     * @throws IllegalArgumentException for an invalid value
     */
    public T validate(Object value) {
        if (!valueClass.isInstance(value)) throw new IllegalArgumentException("Expected " + valueClass.getSimpleName());
        T typed = valueClass.cast(value);
        if (!validator.test(typed)) throw new IllegalArgumentException("Invalid value for " + id);
        return typed;
    }
    /** Returns the exact numeric projection when supported.
     * @param value candidate value
     * @return numeric value or empty
     */
    public Optional<BigDecimal> number(T value) {
        T checked = validate(value);
        return numericValue == null ? Optional.empty() : Optional.of(numericValue.apply(checked));
    }
}
