package zer0g.fusion.data;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class FusionValue implements FusionData, Comparable<FusionValue>, Cloneable
{
    /**
     * Singleton NULL fusion-value, with type {@link FusionValueType#ANY} and value null.<p/> It is not possible to
     * create another FusionValue instance with value of null.
     */
    public static final FusionValue NULL = new FusionValue();
    public static final FusionValue TRUE = new FusionValue(FusionValueType.BOOL, Boolean.TRUE);
    public static final FusionValue FALSE = new FusionValue(FusionValueType.BOOL, Boolean.FALSE);
    public static final FusionValue INT_ZERO = new FusionValue(FusionValueType.INTEGER, Integer.valueOf(0));
    public static final FusionValue BYTE_ZERO = new FusionValue(FusionValueType.INTEGER, Byte.valueOf((byte) 0));
    public static final FusionValue SHORT_ZERO = new FusionValue(FusionValueType.INTEGER, Short.valueOf((short) 0));
    public static final FusionValue LONG_ZERO = new FusionValue(FusionValueType.INTEGER, Long.valueOf(0));
    public static final FusionValue BIGINT_ZERO = new FusionValue(FusionValueType.INTEGER, BigInteger.ZERO);
    public static final FusionValue DECIMAL_ZERO = new FusionValue(FusionValueType.DECIMAL, BigDecimal.ZERO);
    public static final FusionValue FLOAT_ZERO = new FusionValue(FusionValueType.DECIMAL, Float.valueOf(0));
    public static final FusionValue DOUBLE_ZERO = new FusionValue(FusionValueType.DECIMAL, Double.valueOf(0));
    public static final FusionValue DATE_ZERO = new FusionValue(FusionValueType.DATE, LocalDate.ofEpochDay(0));
    public static final FusionValue TIME_ZERO = new FusionValue(FusionValueType.TIME, LocalTime.ofSecondOfDay(0));
    public static final FusionValue DATETIME_ZERO =
          new FusionValue(FusionValueType.DATETIME, LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC));
    public static final FusionValue INSTANT_ZERO = new FusionValue(FusionValueType.INSTANT, Instant.ofEpochSecond(0));
    public static final FusionValue DURATION_ZERO = new FusionValue(FusionValueType.DURATION, Duration.ofSeconds(0));

    public static final FusionValue BLOB_ZERO = new FusionValue(FusionValueType.BLOB, new Blob(0).ensureReadonly());
    public static final FusionValue STRING_EMPTY = new FusionValue(FusionValueType.STRING, "");


    public static void main(String[] args) {
        var reader = new InputStreamReader(System.in);
        while (true) {
            System.out.println("Enter a fusion value:");
            try {
                var value = FusionValueType.ANY.read(reader);
                System.out.println("You entered a " + value.type().name() + " : " + value);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * @return the value's fusion-type.
     */
    @Override
    public FusionValueType type() {
        return _type;
    }

    /**
     * Write fusion-value to the supplied writer in the fusion default "wire-data-format" (JSON).
     *
     * @param writer
     * @throws IOException
     */
    @Override
    public void writeTo(Writer writer) throws IOException {
        type().write(writer, this);
    }

    /**
     * Convenience method for calling {@link FusionValueType#from} of {@link FusionValueType#ANY} with specified java
     * value and null domain.
     *
     * @param javaVal
     *       the java value with which to create any fusion-value
     * @return
     */
    public static FusionValue from(Object javaVal) {
        return FusionValueType.ANY.from(javaVal, null);
    }

    private final Object _value;
    private final FusionValueType _type;

    /**
     * Creates fusion-value with specified type and java-value. Note: Value MUST BE immutable
     *
     * @param type
     *       the required type qualifier
     * @param value
     *       a valid non-null value for the type.  MUST BE IMMUTABLE.  NO CHECK IS PERFORMED HERE.
     * @see FusionValueType#immutable(Object, FusionValueDomain)
     */
    FusionValue(FusionValueType type, Object value) {
        _type = Objects.requireNonNull(type);
        _value = Objects.requireNonNull(value);
    }

    /**
     * Creates fusion-value with type ANY and java-value of null.
     */
    private FusionValue() {
        _value = null;
        _type = FusionValueType.ANY;
    }

    /**
     * Converts {@link #get()} java-value to the one desired by user, if possible.
     *
     * @param desiredClass
     *       the class user wishes to convert to
     * @param <T>
     * @return instance of the desired class that created from the java-value
     * @throws ArithmeticException
     *       if conversion would cause data loss or is not really possible (i.e. the two class types should not be
     *       mixed)
     */
    public <T> T convert(Class<T> desiredClass) throws ArithmeticException {
        if (isNull()) {
            return null;
        }
        assert type() != FusionValueType.ANY;
        if (desiredClass.isAssignableFrom(get().getClass())) {
            return (T) get();
        }
        return type().convert(get(), desiredClass);
    }

    public boolean isNull() {
        return get() == null;
    }

    /**
     * @return the contained java-value.
     */
    public Object get() {
        return _value;
    }

    /**
     * Same as {@link #get()} except that if the fusion-value type is {@link FusionValueType#OBJECT} and the value is
     * {@link FusionRecordObject}, then the java record is extracted from the class and returned.
     *
     * @return
     * @throws ClassCastException
     */
    public Object getinner() {
        if (isNull()) {
            return null;
        }
        if (type() == FusionValueType.OBJECT && get() instanceof FusionRecordObject<?>) {
            return ((FusionRecordObject) get()).extract();
        } else {
            return get();
        }
    }

    @Override
    public int compareTo(FusionValue o) {
        if (type() != o.type()) {
            throw new ClassCastException(type() + " not-comparable with " + o.type());
        }
        if (get() == o.get()) {
            return 0;
        }
        if (isNull()) {
            return -1;
        }
        if (o.isNull()) {
            return 1;
        }
        return type().compareJava(get(), o.get());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(get());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof FusionValue o) {
            if (type() != o.type()) {
                return false;
            }
            if (get() == o.get()) {
                return true;
            }
            if (isNull() || o.isNull()) {
                return false;
            }
            return get().equals(o.get());
        }
        return false;
    }

    @Override
    public FusionValue clone() {
        if (isNull()) {
            return NULL;
        } else {
            try {
                return (FusionValue) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }

    @Override
    public String toString() {
        try (StringWriter writer = new StringWriter()) {
            writeTo(writer);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void accept(FusionValueVisitor visitor) {
        try {
            type().accept(visitor, get());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T as(Class<T> javaClass) {
        return javaClass.cast(get());
    }
}
