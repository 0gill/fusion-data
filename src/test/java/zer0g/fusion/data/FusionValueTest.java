package zer0g.fusion.data;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FusionValueTest
{
    enum Enum1
    {
        A,
        B,
        C
    }

    enum Enum2
    {
        A,
        B,
        C
    }

    @Test
    void testEquals() {
        assertAll(() -> assertEquals(FusionValue.from(5), FusionValue.from(5)),
                  () -> assertNotEquals(FusionValue.from((short) 5), FusionValue.from(5)),
                  () -> assertEquals(FusionValue.from(Path.of("/foo")), FusionValue.from(Path.of("/foo"))),
                  () -> assertEquals(FusionValue.from(Enum1.A), FusionValue.from(Enum1.A)),
                  () -> assertNotEquals(FusionValue.from(Enum1.A), FusionValue.from(Enum2.A)));
    }
}