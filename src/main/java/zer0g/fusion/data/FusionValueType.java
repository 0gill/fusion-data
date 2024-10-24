package zer0g.fusion.data;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static zer0g.fusion.data.NoCaseString.nocase;

public enum FusionValueType implements FusionDataType<FusionValue>
{
    ANY(null) {
        @Override
        <V> V convert(Object o, Class<V> desiredClass) {
            // Every ANY fusion-value will have value of null.
            throw new AssertionError();
        }

        @Override
        protected FusionValue _from(Object javaVal, FusionValueDomain domain) {
            var qtype = FusionValueType.typeForJavaValue(javaVal);
            assert qtype.type != ANY;
            return qtype.type._from(javaVal, new FusionValueDomain(qtype, FusionValue.NULL));
        }

        @Override
        Object immutable(Object javaVal, FusionValueDomain domain) {
            throw new AssertionError();
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            assert javaVal == null;
            visitor.visitNull();
        }

        @Override
        public boolean isScalar() {
            return false;
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            throw new AssertionError();
        }
    },
    BOOL(Boolean.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.FALSE;
        }

        @Override
        protected FusionValue _from(Object javaVal, FusionValueDomain domain) {
            return (Boolean) javaVal ? FusionValue.TRUE : FusionValue.FALSE;
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitBool((Boolean) javaVal);
        }
    },
    INTEGER(Integer.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return switch (domain.qualifier()) {
                case null -> FusionValue.INT_ZERO;
                case "byte" -> FusionValue.BYTE_ZERO;
                case "short" -> FusionValue.SHORT_ZERO;
                case "long" -> FusionValue.LONG_ZERO;
                case "big" -> FusionValue.BIGINT_ZERO;
                default -> throw badQualifier(domain.qualifier());
            };
        }

        @Override
        public Class javaDataClass(String qualifier) {
            return switch (qualifier) {
                case null -> javaDataClass();
                case "byte" -> Byte.class;
                case "short" -> Short.class;
                case "long" -> Long.class;
                case "big" -> BigInteger.class;
                default -> throw badQualifier(qualifier);
            };
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            return compileNumberRange(range, (Class<? extends Number>) javaDataClass(qualifier));
        }

        static boolean isIntType(Number v) {
            return !(v instanceof Float || v instanceof Double || v instanceof BigDecimal);
        }

        @Override
        <V> V convert(Object javaVal, Class<V> desiredClass) {
            if (javaVal.getClass() == desiredClass) {
                return (V) javaVal;
            }

            Number v = (Number) javaVal;
            if (!isIntType(v)) {
                throw new IllegalArgumentException("Invalid java-value type: " + v.getClass());
            }

            // First, see if conversion is a "widening cast" (e.g. short to long)
            if (desiredClass == BigInteger.class) {
                return (V) BigInteger.valueOf(v.longValue());
            } else if (desiredClass == Long.class) {
                if (v instanceof Integer || v instanceof Short || v instanceof Byte) {
                    return (V) Long.valueOf(v.intValue());
                }
            } else if (desiredClass == Integer.class) {
                if (v instanceof Short || v instanceof Byte) {
                    return (V) Integer.valueOf(v.intValue());
                }
            } else if (desiredClass == Short.class) {
                if (v instanceof Byte) {
                    return (V) Short.valueOf(v.byteValue());
                }
            } else if (desiredClass == Byte.class) {
                // this can only be a "tightening cast" (e.g. short to byte)
            }

            // If we are here, the conversion must be a "tightening cast" (e.g. long to int)
            BigInteger bigv = v instanceof BigInteger ? (BigInteger) v : BigInteger.valueOf(v.longValue());
            if (desiredClass == Long.class) {
                return (V) Long.valueOf(bigv.longValueExact());
            }
            if (desiredClass == Integer.class) {
                return (V) Integer.valueOf(bigv.intValueExact());
            }
            if (desiredClass == Short.class) {
                return (V) Short.valueOf(bigv.shortValueExact());
            }
            if (desiredClass == Byte.class) {
                return (V) Byte.valueOf(bigv.byteValueExact());
            }
            throw new AssertionError();
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitInteger((Number) javaVal);
        }
    },
    DECIMAL(BigDecimal.class) {
        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitDecimal((Number) javaVal);
        }

        @Override
        public Class javaDataClass(String qualifier) {
            return switch (qualifier) {
                case null -> javaDataClass();
                case "float" -> Float.class;
                case "double" -> Double.class;
                default -> throw badQualifier(qualifier);
            };
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return switch (domain.qualifier()) {
                case null -> FusionValue.DECIMAL_ZERO;
                case "float" -> FusionValue.FLOAT_ZERO;
                case "double" -> FusionValue.DOUBLE_ZERO;
                default -> throw badQualifier(domain.qualifier());
            };
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            return compileNumberRange(range, javaDataClass(qualifier));
        }

        @Override
        <V> V convert(Object javaVal, Class<V> desiredClass) {
            assert javaVal.getClass() != desiredClass;

            if (desiredClass == Double.class) {
                // Narrowing Or widening conversion
                return (V) Double.valueOf(((Number) javaVal).doubleValue());
            } else if (desiredClass == Float.class) {
                // Narrowing Or widening conversion
                return (V) Float.valueOf(((Number) javaVal).floatValue());
            } else if (desiredClass == BigDecimal.class) {
                // Widening conversion
                if (javaVal instanceof BigInteger) {
                    return (V) new BigDecimal((BigInteger) javaVal);
                } else if (javaVal instanceof Double || javaVal instanceof Float) {
                    return (V) BigDecimal.valueOf(((Number) javaVal).doubleValue());
                } else if (javaVal instanceof Number) {
                    return (V) new BigDecimal(((Number) javaVal).longValue());
                }
            }
            throw new AssertionError();
        }
    },
    STRING(String.class) {
        @Override
        public Class javaDataClass(String qualifier) {
            if (null == qualifier) {
                return javaDataClass();
            } else {
                return subtype(qualifier).javaClass();
            }
        }

        public StringSubtype subtype(String name) {
            var t = _stringSubtypeMap.get(name);
            if (t == null) {
                throw badQualifier(name);
            }
            return t;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            return compileLengthRange(range);
        }

        @Override
        protected void validateRange(Object compiledRange, Object value) {
            validateLengthRange(compiledRange, ((String) value).length());
        }

        /**
         * Will only cast String to a subtype class, or a subtype class to String.
         *
         * @param o
         * @param desiredClass
         * @return
         * @param <V>
         */
        @Override
        <V> V convert(Object o, Class<V> desiredClass) {
            if (desiredClass == String.class) {
                var qt = typeForJavaValue(o);
                if (qt.type != STRING) {
                    throw new UnsupportedOperationException("Can only cast String to/from a String \"subtype\"!");
                }
                return (V) o.toString();
            } else {
                var qt = typeForJavaValueClass(desiredClass);
                if (qt.type != STRING) {
                    throw new UnsupportedOperationException("Can only cast String to/from a String \"subtype\"!");
                }
                var qclass = javaDataClass(qt.qualifier);
                if (qclass != desiredClass) {
                    throw new UnsupportedOperationException(
                          "Cannot convert " + qt.qualifier + " string to " + desiredClass.getName());
                }
                return (V) _stringSubtypeMap.get(qt.qualifier).valueOf((String) o);
            }
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return switch (domain.qualifier()) {
                case null -> FusionValue.STRING_EMPTY;
                default -> subtype(domain.qualifier()).zero();
            };
        }


        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitString(javaVal);
        }
    },
    DATE(LocalDate.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.DATE_ZERO;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            return compileRange(range, LocalDate::parse, LocalDate::compareTo);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitDate((LocalDate) javaVal);
        }
    },
    TIME(LocalTime.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.TIME_ZERO;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            // todo: get precision
            return compileRange(range, LocalTime::parse, LocalTime::compareTo);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitTime((LocalTime) javaVal);
        }
    },
    DATETIME(LocalDateTime.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.DATETIME_ZERO;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            // todo: get precision
            return compileRange(range, LocalDateTime::parse, LocalDateTime::compareTo);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitDateTime((LocalDateTime) javaVal);
        }
    },
    INSTANT(Instant.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.INSTANT_ZERO;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            // todo: get precision
            return compileRange(range, Instant::parse, Instant::compareTo);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitInstant((Instant) javaVal);
        }
    },
    DURATION(Duration.class) {
        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.DURATION_ZERO;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            return compileRange(range, Duration::parse, Duration::compareTo);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitDuration((Duration) javaVal);
        }
    },
    OBJECT(FusionObject.class) {
        @Override
        public boolean isScalar() {
            return false;
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return new FusionValue(this, domain._fobType().make());
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitObject((FusionObject) javaVal);
        }

        @Override
        public Class<?> javaDataClass(String qualifier) {
            return qualifier == null ? javaDataClass() : Fusion.fobType(qualifier).javaDataClass();
        }

        @Override
        Object immutable(Object javaVal, FusionValueDomain domain) {
            FusionObject fob = (FusionObject) javaVal;
            if (fob.isKey()) {
                throw new IllegalArgumentException("Cannot assign a key-fob as a value: " + fob);
            }
            return super.immutable(javaVal, domain);
        }

        @Override
        <V> V convert(Object o, Class<V> desiredClass) {
            if (o instanceof Record && desiredClass.isAssignableFrom(FusionRecordObject.class)) {
                return (V) FusionRecordObject.from((Record) o);
            }

            return super.convert(o, desiredClass);
        }

    },
    LIST(List.class) {
        @Override
        public boolean isScalar() {
            return false;
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            var itemdomain = domain._itemDomain();
            var list = new FusionList(domain);
            int min;
            if (itemdomain._compiledRange() != null) {
                min = (int) itemdomain._compiledRange().min();
            } else {
                min = 0;
            }
            for (int i = 0; i < min; i++) {
                list.add(itemdomain.type().zero(itemdomain));
            }
            return new FusionValue(this, list.ensureReadonly());
        }

        @Override
        Object immutable(Object javaVal, FusionValueDomain domain) {
            List list = (List) javaVal;
            if (domain._itemDomain().type() == ANY) {
                if (list instanceof FusionList<?> dualist) {
                    return dualist.ensureReadonly();
                }
            } else {
                if (list instanceof FusionList<?> dualist) {
                    if (domain.equals(dualist.domain())) {
                        return dualist.ensureReadonly();
                    }
                }
            }
            return new FusionList<>(list, domain);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitList((FusionList<?>) javaVal);
        }

        @Override
        public Class<?> javaDataClass(String qualifier) {
            return List.class;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            // todo: extract element range as well (3rd element of the range-list?)
            return compileLengthRange(range);
        }

        @Override
        protected void validateRange(Object compiledRange, Object value) {
            validateLengthRange(compiledRange, ((List) value).size());
        }

    },
    MAP(Map.class) {
        @Override
        public boolean isScalar() {
            return false;
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            var itemdomain = domain._itemDomain();
            var map = new FusionMap(domain);
            int min;
            if (itemdomain._compiledRange() != null) {
                min = (int) itemdomain._compiledRange().min();
            } else {
                min = 0;
            }
            for (int i = 0; i < min; i++) {
                map.put(nocase("key" + i), itemdomain.type().zero(itemdomain));
            }
            return new FusionValue(this, map.ensureReadonly());
        }

        @Override
        Object immutable(Object javaVal, FusionValueDomain domain) {
            Map map = (Map) javaVal;
            if (domain._itemDomain().type() == ANY) {
                if (map instanceof FusionMap duamap) {
                    return duamap.ensureReadonly();
                }
            } else {
                if (map instanceof FusionMap duamap) {
                    if (domain.equals(duamap.domain())) {
                        return duamap.ensureReadonly();
                    }
                }
            }
            return new FusionMap(map, domain);
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitMap((FusionMap<?>) javaVal);
        }

        @Override
        public Class<?> javaDataClass(String qualifier) {
            return Map.class;
        }

        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            // todo: extract element range as well (3rd element of the range-list?)
            return compileLengthRange(range);
        }

        @Override
        protected void validateRange(Object compiledRange, Object value) {
            validateLengthRange(compiledRange, ((Map) value).size());
        }

    },

    ENUM(Enum.class) {
        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitEnum((Enum<?>) javaVal);
        }

        @Override
        public Class<?> javaDataClass(String qualifier) {
            try {
                return qualifier == null ? javaDataClass() : Class.forName(qualifier);
            } catch (ClassNotFoundException e) {
                throw badQualifier(qualifier);
            }
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return new FusionValue(this, javaDataClass(domain.qualifier()).getEnumConstants()[0]);
        }
    },
    BLOB(Blob.class) {
        @Override
        public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
            return compileLengthRange(range);
        }

        @Override
        protected void validateRange(Object compiledRange, Object value) {
            validateLengthRange(compiledRange, (int) ((Blob) value).length());
        }

        @Override
        void accept(FusionValueVisitor visitor, Object javaVal) throws IOException {
            visitor.visitBlob((Blob) javaVal);
        }

        @Override
        public FusionValue zero(FusionValueDomain domain) {
            return FusionValue.BYTE_ZERO;
        }
    };
    public static final QualifiedType QT_MAP_ANY = new QualifiedType(MAP, ANY.name());
    public static final QualifiedType QT_LIST_ANY = new QualifiedType(LIST, ANY.name());

    private static final Map<String, Validator> _validatorMap = new HashMap<>();
    private static final Map<Class, QualifiedType> _classToQtMap = new HashMap<>();
    private static final Map<String, StringSubtype<?>> _stringSubtypeMap = new HashMap();
    private static final Map<Class, QualifiedType> _baseclassToQtMap = new HashMap<>();
    //private static final Map<String, Class<?>> _stringSubtypeClassMap = new HashMap<>();

    /**
     * The base of ALL compiled-ranges.  A particular type, e.g. {@link #STRING}, may extend with "regex"... possible
     * future enhancements.
     *
     * @param <T>
     */
    public interface MinMaxRange<T>
    {
        T min();

        T max();
    }

    public interface StringSubtype<T>
    {
        String name();

        T valueOf(String fromString);

        FusionValue zero();

        Class<T> javaClass();
    }

    static {
        // Register the default (null) qualifiers for each type.  This cannot be done in the base constructor
        // because the static map fields will not yet be constructed.
        for (FusionValueType type : FusionValueType.values()) {
            if (type._javaDataClass != null) {
                registerQualifier(type, null);
            }
        }
        registerQualifier(INTEGER, "byte");
        registerQualifier(INTEGER, "short");
        registerQualifier(INTEGER, "long");
        registerQualifier(INTEGER, "big");
        registerQualifier(DECIMAL, "float");
        registerQualifier(DECIMAL, "double");

        registerStringSubtype("nocase", NoCaseString.class, NoCaseString::new, new NoCaseString(""));
        registerStringSubtype("path", Path.class, (s) -> Path.of(s), null);
        registerStringSubtype("uri", URI.class, (s) -> {
            try {
                return new URI(s);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }, null);
        registerStringSubtype("url", URL.class, (s) -> {
            try {
                return new URL(s);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }, null);
        registerStringSubtype("char", Character.class, (s) -> switch (s.length()) {
            case 0 -> Character.valueOf((char) 0);
            case 1 -> s.charAt(0);
            default -> throw new IllegalArgumentException("String size is more than 1!");
        }, Character.valueOf((char) 0));
    }

    public record Range<T>(T min, T max) implements MinMaxRange<T>
    {
    }

    public record QualifiedType(FusionValueType type, String qualifier)
    {
        public static QualifiedType fromString(String combinedString) {
            int doti = combinedString.indexOf('.');
            if (-1 == doti) {
                return new QualifiedType(FusionValueType.valueOf(combinedString), null);
            } else {
                return new QualifiedType(FusionValueType.valueOf(combinedString.substring(0, doti)),
                                         combinedString.substring(doti + 1));
            }
        }

        public QualifiedType {
            Objects.requireNonNull(type);
        }

        /**
         * @return combined-string representation that can be passed to {@link #fromString(String)}
         */
        @Override
        public String toString() {
            return type.name() + (qualifier != null ? "." + qualifier : "");
        }
    }

    public static synchronized <T> StringSubtype<T> registerStringSubtype(
          String name, Class<T> javaClass, Function<String, T> creator, T zero)
    {
        if (_stringSubtypeMap.containsKey(name)) {
            throw new IllegalArgumentException("String subtype already registered: " + name);
        }
        if (stringSubtypeForClass(javaClass) != null) {
            throw new IllegalArgumentException("String subtype for " + javaClass.getName() + " already registered!");
        }

        registerQualifier(STRING, name, javaClass);
        var subtype = new StringSubtype<T>()
        {
            @Override
            public String name() {
                return name;
            }

            @Override
            public T valueOf(String fromString) {
                return creator.apply(fromString);
            }

            @Override
            public FusionValue zero() {
                return zero != null ? new FusionValue(STRING, zero) : FusionValue.NULL;
            }

            @Override
            public Class<T> javaClass() {
                return javaClass;
            }
        };
        var old = _stringSubtypeMap.put(name, subtype);
        assert old == null;
        return subtype;
    }

    private static <T> StringSubtype<T> stringSubtypeForClass(Class<T> javaClass) {
        return (StringSubtype<T>) _stringSubtypeMap.values().stream().filter(t -> t.javaClass() == javaClass)
                                                   .findFirst().orElse(null);
    }

    static final synchronized void registerQualifier(FusionValueType type, String typeQualifier, Class javaClass) {
        Map<Class, QualifiedType> map;
        var qt = new QualifiedType(type, typeQualifier);
        var oldqt = _classToQtMap.put(javaClass, qt);
        if (null != oldqt) {
            _classToQtMap.put(javaClass, oldqt);
            throw new IllegalArgumentException(
                  "Attempt to map class " + javaClass.getName() + " to " + qt + " AND " + oldqt + "!");
        }
        if (!isFinal(javaClass)) {
            _baseclassToQtMap.put(javaClass, qt);
        }
    }

    private static boolean isFinal(Class javaValueClass) {
        return Modifier.isFinal(javaValueClass.getModifiers()) || javaValueClass == BigDecimal.class ||
               javaValueClass == BigInteger.class;
    }

    public static final FusionValue readAny(String json) throws IOException {
        return ANY.read(new StringReader(json));
    }

    public final FusionValue read(Reader wire, FusionValueDomain domain) throws IOException {
        return new JsonReader(wire).read(this, domain);
    }

    static synchronized void registerValidator(Validator validator) {
        var v = _validatorMap.put(validator.name(), validator);
        if (v != null) {
            _validatorMap.put(v.name(), v);
            throw new IllegalArgumentException("Validation-alg already registered: " + validator.name());
        }
    }

    static synchronized QualifiedType typeForJavaValue(Object javaVal) {
        if (javaVal instanceof FusionObject fo) {
            return new QualifiedType(OBJECT, fo.type().name());
        } else {
            return typeForJavaValueClass(javaVal.getClass());
        }
    }

    static synchronized QualifiedType typeForJavaValueClass(Class javaValueClass) {
        return typeForJavaValueClass(javaValueClass, false);
    }

    static synchronized QualifiedType typeForJavaValueClass(Class javaValueClass, boolean generatorPhase) {
        if (javaValueClass.isPrimitive()) {
            javaValueClass = primitiveBoxClass(javaValueClass);
        }
        if (javaValueClass.isRecord() || FusionObject.class.isAssignableFrom(javaValueClass)) {
            return new QualifiedType(OBJECT, Fusion.fobType(javaValueClass).name());
        }
        if (javaValueClass.isEnum()) {
            return new QualifiedType(ENUM, javaValueClass.getName());
        }
        var qt = _classToQtMap.get(javaValueClass);
        if (qt != null) {
            return qt;
        }
        if (!generatorPhase) {
            for (Map.Entry<Class, QualifiedType> e : _baseclassToQtMap.entrySet()) {
                if (e.getKey().isAssignableFrom(javaValueClass)) {
                    return e.getValue();
                }
            }
        }
        throw new IllegalArgumentException("Class " + javaValueClass.getName() + " does not map to any fusion-type!");
    }

    public static Class<?> primitiveBoxClass(Class<?> primitiveType) {
        return switch (primitiveType.getName()) {
            case "boolean" -> Boolean.class;
            case "char" -> Character.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            default -> throw new IllegalArgumentException(primitiveType.getName());
        };
    }

    static synchronized void registerQualifier(FusionValueType type, String typeQualifier) {
        registerQualifier(type, typeQualifier, type.javaDataClass(typeQualifier));
    }

    /**
     * @param qualifier
     *       non-null type qualifier
     * @return the java-value class for the specified qualifier; null if unknown qualifier
     */
    protected Class<?> javaDataClass(String qualifier) {
        if (qualifier == null) {
            return javaDataClass();
        } else {
            throw badQualifier(qualifier);
        }
    }

    protected IllegalArgumentException badQualifier(String typeQualifier) {
        return new IllegalArgumentException("Bad qualifier for " + name() + ": " + typeQualifier);
    }

    protected static <T> MinMaxRange<T> compileRange(
          FusionValue range, Function<String, T> parser, Comparator<T> comparator)
    {
        var list = (List) range.get();
        String minStr = (String) list.get(0);
        String maxStr = (String) list.get(1);
        T min = minStr != null ? parser.apply(minStr) : null;
        T max = maxStr != null ? parser.apply(maxStr) : null;
        if (max != null && min != null && comparator.compare(max, min) < 0) {
            throw new IllegalArgumentException("Max-value is less than min!");
        }
        return new Range(min, max);
    }

    protected static MinMaxRange<Integer> compileLengthRange(FusionValue range) {
        var result = INTEGER.compileNumberRange(range, Integer.class);
        if (result.min != null && result.min < 0) {
            throw new IllegalArgumentException("Invalid min: " + result.min);
        }
        if (result.max != null && result.max <= 0) {
            throw new IllegalArgumentException("Invalid max: " + result.max);
        }
        if (result.min != null && result.max != null && result.max < result.min) {
            throw new IllegalArgumentException("Invalid size range: max < min");
        }
        return result;
    }

    protected <T extends Number> Range<T> compileNumberRange(
          FusionValue range, Class<? extends T> numberClass)
    {
        var numlist = (List<? extends Number>) range.get();
        Number min = numlist.get(0);
        Number max = (numlist.size() > 1) ? numlist.get(1) : null;
        if (null != min) {
            min = convert(min, numberClass);
        }
        if (null != max) {
            max = convert(max, numberClass);
        }
        return new Range(min, max);
    }

    <V> V convert(Object o, Class<V> desiredClass) {
        throw makeConvertException(o.getClass(), desiredClass);
    }

    final RuntimeException makeConvertException(Class<?> fromClass, Class<?> toClass) {
        throw new ArithmeticException(
              name() + ": cannot auto-convert " + fromClass.getName() + " to " + toClass.getName());
    }

    protected static void validateLengthRange(Object compiledRange, int valueLength) {
        validateRange(compiledRange, valueLength, "length");
    }

    protected static void validateRange(Object compiledRange, Object value, String errpfx) throws ValidationException {
        Range<? extends Comparable> range = (Range<? extends Comparable>) compiledRange;
        if (range.min != null && range.min().compareTo(value) > 0) {
            throw new ValidationException(errpfx + " less than " + range.min + ": " + value);
        }
        if (range.max != null && range.max().compareTo(value) < 0) {
            throw new ValidationException(errpfx + " greater than " + range.max + ": " + value);
        }
    }

    private final Class<?> _javaDataClass;

    FusionValueType(Class<?> javaDataClass) {
        _javaDataClass = javaDataClass;
    }

    public boolean isScalar() {
        return true;
    }

    /**
     * Returns a fusion-value whose wrapped value is NOT NULL.  The wrapped value should be the "zero" value for the
     * type.  For primitive (or their wrapped) types, the default is the same as in Java.  For Strings, List, and Map
     * types, the default should be "empty".  For Object, the default should be the result of
     * {@link FusionObjectType#make()}.
     *
     * @param domain
     *       nullable qualifier for the type
     * @return
     */
    public abstract FusionValue zero(FusionValueDomain domain);

    public final FusionValueDomain defaultDomain() {
        return new FusionValueDomain(this, null, FusionValue.NULL);
    }

    public MinMaxRange<?> compileRange(FusionValue range, String qualifier) {
        throw new IllegalArgumentException(this + " type does not support range!");
    }

    @Override
    public Validator<?> validatorFor(String algName) {
        return validator(algName);
    }

    public static synchronized Validator validator(String algName) {
        var v = _validatorMap.get(algName);
        if (null == v) {
            throw new IllegalArgumentException("Unknown validator: " + algName);
        }
        return v;
    }

    @Override
    public final FusionValue read(Reader wire) throws IOException {
        return read(wire, null);
    }

    @Override
    public void write(Writer writer, FusionValue fv) throws IOException {
        fv.accept(new JsonWriter(writer));
    }

    @Override
    public final Class<?> javaDataClass() {
        return _javaDataClass;
    }

    public FusionValue readJson(String json, FusionValueDomain domain) {
        if (null == json) {
            return null;
        }
        try (var wire = new StringReader(json)) {
            try {
                return read(wire, domain);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    public final FusionValue from(Object javaVal, FusionValueDomain domain) {
        assert null == domain || this == domain.type();
        if (javaVal == null || javaVal == FusionValue.NULL) {
            return FusionValue.NULL;
        }
        FusionValue result;
        if (javaVal instanceof FusionValue fv) {
            assert !fv.isNull();
            assert fv.type() != ANY;
            if (this == ANY) {
                return fv;
            } else if (this == fv.type()) {
                result = _from(fv.get(), domain);
            } else {
                throw new IllegalArgumentException("Cannot assign " + fv.type() + " to " + this);
            }
        } else {
            result = _from(javaVal, domain);
        }
        if (null != domain && !domain.range().isNull()) {
            validateRange(domain._compiledRange(), result.get());
        }
        return result;
    }

    protected FusionValue _from(Object javaVal, FusionValueDomain domain) {
        Class<?> desiredClass = (domain == null) ? javaDataClass() : javaDataClass(domain.qualifier());

        if (javaVal.getClass() == desiredClass || desiredClass.isAssignableFrom(javaVal.getClass())) {
            return new FusionValue(this, immutable(javaVal, domain));
        } else {
            return new FusionValue(this, convert(javaVal, desiredClass));
        }
    }

    protected void validateRange(Object compiledRange, Object value) {
        validateRange(compiledRange, value, "value");
    }

    /**
     * @param javaVal
     *       the java-value that needs to be immutable
     * @param domain
     * @return the supplied java-value if it is already immutable, otherwise an immutable copy of the java-value
     */
    Object immutable(Object javaVal, FusionValueDomain domain) {
        if (javaVal instanceof InitWriteReadStateData iwrval) {
            // TODO: require that it be readonly?
            // iwrval.state().requireReadonly();
            if (!iwrval.state().isReadonly()) {
                iwrval.doneWrite();
            }
            return iwrval;
        } else {
            return javaVal;
        }
    }

    abstract void accept(FusionValueVisitor visitor, Object javaVal) throws IOException;

    int compareJava(Object v1, Object v2) {
        return ((Comparable) v1).compareTo(v2);
    }
}
