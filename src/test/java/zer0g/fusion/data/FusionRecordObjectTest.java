package zer0g.fusion.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FusionRecordObjectTest
{
    @BeforeAll
    static void init() {
        //FusionObjects.load();
    }

    @Test
    void of() {
    }

    @Test
    void set() {
    }

    @Test
    void extract() {
    }

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    record TestRecord1(int foo, long bar){}
    record TestRecord2(int foo, long _bar){}

    @Test
    void from() {
        FusionValueDomain rec = null;
        try {
            rec = new FusionValueDomain(FusionValueType.STRING, null, FusionValueType.readAny("[null,55]"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var fo1 = FusionRecordObject.from(rec);
        var fo2 = FusionRecordObject.of(rec.getClass());
        assertNotEquals(fo1, fo2);
        fo2.set("type", rec.type());
        fo2.set("qualifier", rec.qualifier());
        fo2.set("range", rec.range());
        assertEquals(fo1,fo2);
    }
}