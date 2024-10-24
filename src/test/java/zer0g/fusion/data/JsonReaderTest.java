package zer0g.fusion.data;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JsonReaderTest
{
    @FoType(fieldOrder = {"x"})
    interface TestBean extends FusionBean
    {
        int getX();

        TestBean setX(int value);

        double getY();

        TestBean setY(double value);
    }

    @BeforeAll
    static void init() {
        //FusionObjects.load();
    }

    @Test
    void read() throws IOException {
        var testBeanType = (FusionBeanObjectType<TestBean>) Fusion.fobType(TestBean.class);
        assertAll(() -> assertEquals(read("null"), FusionValue.NULL),
                  () -> assertEquals(read("true"), FusionValue.TRUE),
                  () -> assertEquals(read("false"), FusionValue.FALSE),
                  () -> assertEquals(read("[]"), FusionValue.from(List.of())),
                  () -> assertEquals(read("{}"), FusionValue.from(new FusionMap<>(FusionValueDomain.MAP_ANY))),
                  () -> assertEquals(read("0"), FusionValue.from(Integer.valueOf(0))),
                  () -> assertEquals(read("0"), FusionValue.from(0)),
                  () -> assertEquals(read("1000000"), FusionValue.from(1000_000)),
                  () -> assertNotEquals(read("0"), FusionValue.from(0L)),
                  () -> assertNotEquals(read("0"), FusionValue.from(BigInteger.valueOf(0))),
                  () -> assertNotEquals(read("0"), FusionValue.from((short) 0)),
                  () -> assertEquals(read("0.0"), FusionValue.from(BigDecimal.valueOf(0.0))),
                  () -> assertEquals(read("{\"x\":5,\"y\":15.5}", new FusionValueDomain(testBeanType)),
                                     FusionValue.from(testBeanType.make().setX(5).setY(15.5))));
    }

    private void assertEquals(FusionValue val1, FusionValue val2) {
        Assertions.assertEquals(val1, val2);
        Assertions.assertEquals(val1.get(), val2.get());
    }

    FusionValue read(String json) throws IOException {
        return read(json, FusionValueType.ANY.defaultDomain());
    }

    FusionValue read(String json, FusionValueDomain domain) throws IOException {
        return new JsonReader(new StringReader(json)).read(domain.type(), domain);
    }
}