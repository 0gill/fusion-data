package zer0g.fusion.data;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An identifier that is similar to a java-identifier in format and is:
 * <ul>
 *     <li>CASE INSENSITIVE</li>
 *     <li>Comprised of one or more "parts", joined together with a dot (e.g. "part1.part2")</li>
 * </ul>
 * <p/>
 */
public class Ident implements Comparable<Ident>
{
    public static final String PATTERN_STR = "(" + Simple.PATTERN_STR + ")(\\." + Simple.PATTERN_STR + ")*";
    protected static final Pattern PATTERN = Pattern.compile(PATTERN_STR);

    static {
        FusionValueType.registerStringSubtype("dotident", Ident.class, s -> new Ident(s), null);
        FusionValueType.registerStringSubtype("ident", Simple.class, s -> new Simple(s), null);
    }

    /**
     * A simple one-part name (no dots)
     */
    public static final class Simple extends Ident
    {
        //public static final String PATTERN_STR = "[_a-zA-Z][_a-zA-Z0-9]*";
        public static final String PATTERN_STR = "[_\\p{L}][_\\p{L}\\d]*";
        protected static final Pattern PATTERN = Pattern.compile(PATTERN_STR);

        public static boolean isValid(String val) {
            return PATTERN.matcher(val).matches();
        }

        public Simple(String val) {
            super(val);
        }

        @Override
        public Simple head() {
            return this;
        }

        @Override
        public Simple getPart(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException(index);
            }
            return this;
        }

        @Override
        public Ident tail() {
            return null;
        }

        @Override
        public Ident head(int count) {
            if (count == 1) {
                return this;
            } else if (count == 0) {
                return null;
            } else {
                throw new IndexOutOfBoundsException(count);
            }
        }

        @Override
        public int count() {
            return 1;
        }

        @Override
        public List<Simple> split() {
            return List.of(this);
        }
    }
    private final String _value;

    public Ident(Ident... parts) {
        this(List.of(parts));
    }

    public Ident(List<? extends Ident> parts) {
        this(combine(parts.stream().map(Ident::toString).toArray()));
    }

    public Ident(String val) {
        if (isValid(val)) {
            _value = val;
        } else {
            throw new IllegalArgumentException("Not a valid ident: " + val);
        }
    }

    private static String combine(Object[] parts) {
        if (parts.length == 0) {
            return "";
        } else if (parts.length == 1) {
            return (String) parts[0];
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append((String) parts[0]);
            for (int i = 1; i < parts.length; i++) {
                buf.append('.' + (String) parts[i]);
            }
            return buf.toString();
        }
    }

    public static boolean isValid(String val) {
        return PATTERN.matcher(val).matches();
    }

    public Ident(String... parts) {
        this(combine(parts));
    }

    public Ident(Ident base, String rest) {
        this(List.of(base, Ident.valueOf(rest)));
    }

    /**
     * @param val
     *       string value
     * @return Name wrapper around string value, validated against Dotted.PATTERN; null if value is null
     */
    public static Ident valueOf(String val) {
        if (val == null || val.isEmpty()) {
            return null;
        } else {
            return new Ident(val);
        }
    }

    @Override
    public int hashCode() {
        return _value.toLowerCase().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof Ident) {
            return compareTo((Ident) obj) == 0;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return _value;
    }

    @Override
    public int compareTo(Ident o) {
        return _value.compareToIgnoreCase(o._value);
    }

    public boolean isPrefixOf(Ident o) {
        if (o == null || o._value.length() < _value.length()) {
            return false;
        }
        if (o._value.length() > _value.length() && o._value.charAt(_value.length()) != '.') {
            return false;
        }
        return o._value.substring(0, _value.length()).compareToIgnoreCase(_value) == 0;
    }

    public Simple head() {
        return getPart(0);
    }

    public Simple getPart(int index) {
        return split().get(index);
    }

    public Ident tail() {
        var t = split();
        if (t.size() > 1) {
            var t2 = t.subList(1, t.size());
            return new Ident(t2);
        } else {
            return null;
        }
    }

    public List<Simple> split() {
        return split(_value);
    }

    public static List<Simple> split(String combined) {
        String[] parts = combined.split("\\.");
        List<Simple> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add(new Simple(part));
        }
        return result;
    }

    public Ident head(int count) {
        if (count == 1) {
            return head();
        } else if (count == 0) {
            return null;
        } else if (count < 0) {
            throw new IndexOutOfBoundsException(count);
        }
        var parts = split();
        if (parts.size() >= count) {
            return new Ident(parts.subList(0, count - 1));
        } else {
            throw new IndexOutOfBoundsException(count);
        }
    }

    public int count() {
        return split().size();
    }

    public final Ident dot(String tail) {
        return new Ident(this, valueOf(tail));
    }
}