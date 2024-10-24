package zer0g.fusion.data;

import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;

public class JsonWriter extends FusionValueVisitor
{
    private final Writer _writer;

    public JsonWriter(Writer writer) {
        _writer = Objects.requireNonNull(writer);
    }

    /**
     *
     */
    @Override
    void visitNull() throws IOException {
        _writer.write("null");
    }

    /**
     * @param value
     */
    @Override
    void visitBool(Boolean value) throws IOException {
        _writer.write(value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitInteger(Number value) throws IOException {
        _writer.write(value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitDecimal(Number value) throws IOException {
        _writer.write(value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitString(Object value) throws IOException {
        encodestr(_writer, value.toString());
    }

    public static void encodestr(Writer w, String value) throws IOException {
        w.write('"');
        for (int i = 0; i < value.length(); i++) {
            char cp = value.charAt(i);
            switch (cp) {
                case '\t':
                    w.write('\t');
                    break;
                case '\r':
                    w.write('\r');
                    break;
                case '\n':
                    w.write('\n');
                    break;
                case '\b':
                    w.write('\b');
                    break;
                case '\f':
                    w.write('\f');
                    break;
                case '\\':
                    w.write('\\');
                    break;
                case '"':
                    w.write('"');
                    break;
                default:
                    if (cp < 0x0020) {
                        w.write(String.format("\\u%04X", (int) cp));
                    } else {
                        w.write(cp);
                    }
                    break;
            }
        }
        w.write('"');
    }

    /**
     * @param value
     */
    @Override
    void visitDate(LocalDate value) throws IOException {
        encodestr(_writer, value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitTime(LocalTime value) throws IOException {
        encodestr(_writer, value.toString());
    }

    @Override
    void visitDateTime(LocalDateTime value) throws IOException {
        encodestr(_writer, value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitInstant(Instant value) throws IOException {
        encodestr(_writer, value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitDuration(Duration value) throws IOException {
        encodestr(_writer, value.toString());
    }

    /**
     * @param value
     */
    @Override
    void visitList(FusionList<?> value) throws IOException {
        _writer.write('[');
        boolean first = true;
        for (FusionValue fusionValue : value._inner) {
            if (!first) {
                _writer.write(',');
            } else {
                first = false;
            }
            fusionValue.accept(this);
        }
        _writer.write(']');
    }

    @Override
    void visitMap(FusionMap<?> value) throws IOException {
        visitMap(value.inner());
    }

    private void visitMap(Map<NoCaseString, FusionValue> map) throws IOException {
        _writer.write('{');
        boolean first = true;
        for (var key : map.keySet()) {
            if (!first) {
                _writer.write(',');
            } else {
                first = false;
            }
            encodestr(_writer, key.toString());
            _writer.write(':');
            map.get(key).accept(this);
        }
        _writer.write('}');
    }

    @Override
    void visitEnum(Enum<?> value) throws IOException {
        encodestr(_writer, value.name());
    }

    /**
     * @param fob
     */
    @Override
    void visitObject(FusionObject fob) throws IOException {
        Map<NoCaseString, FusionValue> kvMap;
        if (fob.isKey()) {
            kvMap = Map.ofEntries(fob.asMap().entrySet().stream().filter(e -> fob.schema().field(e.getKey()).isKey())
                                     .toList().toArray(new Map.Entry[0]));
        } else {
            kvMap = fob.asMap();
        }
        visitMap(kvMap);
    }

    /**
     * @param value
     */
    @Override
    void visitBlob(Blob value) throws IOException {
        encodestr(_writer, value.toString());
    }
}
