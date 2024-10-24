package zer0g.fusion.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class IwrListTest
{
    @Test
    void iwr() {
        var list = new IwrList<String>(ArrayList.class);
        assertDoesNotThrow(() -> list.add("A"));
        list.doneWrite();
        assertThrows(RuntimeException.class, () -> list.add("A"));
        assertThrows(RuntimeException.class, () -> list.remove("A"));
        assertThrows(RuntimeException.class, () -> list.remove(0));
        assertThrows(RuntimeException.class, () -> list.addAll(null));
        assertThrows(RuntimeException.class, () -> list.removeAll(null));
    }

}