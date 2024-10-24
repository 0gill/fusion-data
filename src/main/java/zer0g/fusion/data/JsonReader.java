package zer0g.fusion.data;

import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static zer0g.fusion.data.FusionValueType.*;

public class JsonReader extends FusionValueReader.Base
{
    private final Reader _wire;

    public JsonReader(Reader wire) {
        if (!wire.markSupported()) {
            throw new IllegalArgumentException("Supplied reader must support mark (with read-ahead-limit of 1)!");
        }
        this._wire = Objects.requireNonNull(wire);
    }

    @Override
    public FusionValue read(FusionValueType type, FusionValueDomain domain) throws IOException {
        if (skipws() == 'n') {
            expect("null");
            return FusionValue.NULL;
        }
        return super.read(type, domain);
    }

    @Override
    protected FusionValue readAny() throws IOException {
        return switch (peek()) {
            case '{' -> readMap(null);
            case '[' -> readList(null);
            case 't', 'f' -> readBool();
            case '"' -> readString(null);
            default -> readNumber();
        };
    }

    @Override
    protected FusionValue readBool() throws IOException {
        switch (peek()) {
            case 't' -> {
                expect("true");
                return FusionValue.TRUE;
            }
            case 'f' -> {
                expect("false");
                return FusionValue.FALSE;
            }
            default -> throw new IOException("Unexpected start for boolean: " + (char) peek());
        }
    }

    @Override
    protected FusionValue readInteger(FusionValueDomain domain) throws IOException {
        var intstr = jreadIntPart();
        expectAtTokenBreak();
        return INTEGER.from(new BigInteger(intstr), domain);
    }

    @Override
    protected FusionValue readDecimal(FusionValueDomain domain) throws IOException {
        return DECIMAL.from(jreadNumber(), domain);
    }

    @Override
    protected FusionValue readString(FusionValueDomain domain) throws IOException {
        return STRING.from(jreadString(), domain);
    }

    @Override
    public FusionValue readDate(FusionValueDomain domain) throws IOException {
        assert domain.type() == DATE;
        return fromString(domain, LocalDate::parse);
    }

    @Override
    public FusionValue readTime(FusionValueDomain domain) throws IOException {
        assert domain.type() == TIME;
        return fromString(domain, LocalTime::parse);
    }

    @Override
    protected FusionValue readDateTime(FusionValueDomain domain) throws IOException {
        assert domain.type() == DATETIME;
        return fromString(domain, LocalDateTime::parse);
    }

    @Override
    protected FusionValue readInstant(FusionValueDomain domain) throws IOException {
        assert domain.type() == INSTANT;
        return fromString(domain, Instant::parse);
    }

    @Override
    protected FusionValue readDuration(FusionValueDomain domain) throws IOException {
        assert domain.type() == DURATION;
        return fromString(domain, Duration::parse);
    }

    @Override
    protected FusionValue readObject(FusionValueDomain domain) throws IOException {
        FusionObjectType fotype = Fusion.fobType(domain != null ? domain.qualifier() : null);
        var fo = (FusionObjectBase) fotype.make();
        assert fo.type() == fotype;
        readMap(fo.asMap(), (key) -> null == domain ? FusionValueDomain.ANY : fo.schema().field(key).domain());
        return OBJECT.from(fo, domain);
    }

    @Override
    protected FusionValue readList(FusionValueDomain domain) throws IOException {
        if (domain == null) {
            domain = FusionValueDomain.LIST_ANY;
        }
        expect('[');
        if (skipws() == ']') {
            read();
            return LIST.from(List.of(), domain);
        }
        var list = new FusionList<>(domain);
        while (true) {
            list.add(read(list.valueDomain().type(), list.valueDomain()));
            skipws();
            int next = read();
            if (next == ']') {
                break;
            }
            if (next != ',') {
                throw new IOException("Expected , or ] but got " + Character.toString(next));
            }
            if (domain._compiledRange() != null && (int) domain._compiledRange().max() < list.size()) {
                throw new IOException("List longer than max: " + domain._compiledRange().max());
            }
        }
        return LIST.from(list, domain);
    }

    @Override
    protected FusionValue readMap(FusionValueDomain domain) throws IOException {
        if (null == domain) {
            domain = FusionValueDomain.MAP_ANY;
        }
        var map = new FusionMap(domain);
        readMap(map.inner(), key -> map.valueDomain());
        return MAP.from(map, domain);
    }

    @Override
    protected FusionValue readEnum(FusionValueDomain domain) throws IOException {
        var c = domain.type().javaDataClass(domain.qualifier());
        var name = jreadString();
        for (Object o : c.getEnumConstants()) {
            var e = (Enum<?>) o;
            if (e.name().equals(name)) {
                return ENUM.from(e, domain);
            }
        }
        throw new IOException("Invalid enum-constant for " + domain.qualifier() + ": " + name);
    }

    @Override
    public FusionValue readBlob(FusionValueDomain domain) throws IOException {
        return fromString(domain, Blob::new);
    }

    private FusionValue fromString(FusionValueDomain domain, Function<String, Object> parser) throws IOException {
        return domain.type().from(parser.apply(jreadString()), domain);
    }

    protected String jreadString() throws IOException {
        if (read() != '\"') {
            throw new IOException("Unexpected string start!");
        }
        StringBuilder buf = new StringBuilder();
        int cp;
        do {
            cp = read();
            if (cp == -1) {
                throw new EOFException();
            }
            if (cp == '"') {
                break;
            }
            if (cp == '\\') {
                switch (read()) {
                    case '"' -> cp = '"';
                    case '\\' -> {
                    }
                    case '/' -> cp = '/';
                    case 'b' -> cp = '\b';
                    case 'f' -> cp = '\f';
                    case 'n' -> cp = '\n';
                    case 'r' -> cp = '\r';
                    case 't' -> cp = '\t';
                    case 'u' -> {
                        String hex = readmany(4);
                        cp = Integer.valueOf(hex, 16).shortValue();
                    }
                }
            }
            buf.append((char) cp);
        } while (true);
        expectAtTokenBreak();
        return buf.toString();
    }

    private String readmany(int count) throws IOException {
        var result = new StringBuilder();
        while (--count >= 0) {
            int cp = read();
            if (cp == -1) {
                throw new EOFException();
            }
            result.append((char) cp);
        }
        return result.toString();
    }

    /**
     * Reads json 'integer' grammar element.
     */
    protected String jreadIntPart() throws IOException {
        mark(1);
        int cp = read();
        if (cp == '-') {
            return "-" + jreadDigits();
        } else {
            reset();
            return jreadDigits();
        }
    }

    protected void expectAtTokenBreak() throws IOException {
        int c = peek();
        switch (c) {
            case '\n', '\r', ' ', '\t', ',', ':', '{', '}', '[', ']', -1 -> {
            }
            default -> throw new IOException("Unexpected: " + (char) c);
        }
    }

    protected void mark(int readAheadLimit) throws IOException {
        _wire.mark(readAheadLimit);
    }

    protected int read() throws IOException {
        return _wire.read();
    }

    /**
     * Reads json 'digits' grammar element.
     */
    protected String jreadDigits() throws IOException {
        var buf = new StringBuilder();
        int cp;
        do {
            mark(1);
            cp = read();
            if (cp >= '0' && cp <= '9') {
                buf.append((char) cp);
            } else {
                reset();
                break;
            }
        } while (true);
        if (buf.isEmpty()) {
            throw new IOException("No digits!");
        }
        return buf.toString();
    }

    protected void reset() throws IOException {
        _wire.reset();
    }

    protected int peek() throws IOException {
        mark(1);
        try {
            return read();
        } finally {
            reset();
        }
    }

    protected FusionValue readNumber() throws IOException {
        String intPart, fracPart, expoPart;
        intPart = jreadIntPart();
        fracPart = jreadFracPart();
        expoPart = jreadExpoPart();
        expectAtTokenBreak();
        if (fracPart.isEmpty() && expoPart.isEmpty()) {
            return INTEGER.from(new BigInteger(intPart), null);
        } else {
            return DECIMAL.from(new BigDecimal(intPart + fracPart + expoPart), null);
        }
    }

    protected void expect(char tokenBreak) throws IOException {
        int got = read();
        if (got != tokenBreak) {
            throw new IOException("Expected " + tokenBreak + " but got " + (char) got);
        }
    }

    protected void expect(String token) throws IOException {
        var got = readmany(token.length());
        if (!got.equals(token)) {
            throw new IOException("Expected '" + token + "' but got '" + got + "'");
        }
        expectAtTokenBreak();
    }

    protected BigDecimal jreadNumber() throws IOException {
        var bigd = new BigDecimal(jreadIntPart() + jreadFracPart() + jreadExpoPart());
        expectAtTokenBreak();
        return bigd;
    }

    /**
     * Reads json 'fraction' grammar element.
     */
    protected String jreadFracPart() throws IOException {
        mark(1);
        int cp = read();
        if (cp == '.') {
            return "." + jreadDigits();
        } else {
            reset();
            return "";
        }
    }

    /**
     * Reads json 'exponent' grammar element.
     */
    protected String jreadExpoPart() throws IOException {
        mark(1);
        int cp = read();
        if (cp != 'E' && cp != 'e') {
            reset();
            return "";
        }
        mark(1);
        cp = read();
        String sign;
        if (cp == '+' || cp == '-') {
            sign = "" + (char) cp;
        } else {
            reset();
            sign = "";
        }

        return "e" + sign + jreadDigits();
    }

    protected int skipws() throws IOException {
        while (true) {
            mark(1);
            int c = read();
            switch (c) {
                case '\n':
                case '\r':
                case ' ':
                case '\t':
                    continue;
                default:
                    reset();
                case -1:
                    return c;
            }
        }
    }

    protected int read(char[] cbuf, int off, int len) throws IOException {
        return _wire.read(cbuf, off, len);
    }

    private void readMap(Map<NoCaseString, FusionValue> fvmap, Function<NoCaseString, FusionValueDomain> domainGetter)
          throws IOException
    {
        skipws();
        expect('{');
        if (peek() == '}') {
            read();
        } else {
            while (true) {
                var key = new NoCaseString(jreadString());
                skipws();
                expect(':');

                try {
                    var domain = domainGetter.apply(key);
                    fvmap.put(key, read(domain.type(), domain));
                } catch (Exception e) {
                    throw new IOException("Error reading value for field '" + key + "' because: " + e.getMessage(), e);
                }

                skipws();
                int next = read();
                if (next == '}') {
                    break;
                }
                if (next != ',') {
                    throw new IOException("Expected , or } but got " + Character.toString(next));
                }
            }
        }
    }
}
