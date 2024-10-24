package zer0g.fusion.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static zer0g.fusion.data.NoCaseString.nocase;

class FusionBeanObjectTest
{
    static FusionBeanObjectType<TestBean> beanFactory;
    static FusionBeanObjectType<TestBean2> beanFactory2;
    static FusionBeanObjectType<TestBeanContainer> containerFactory;

    @FoType(fieldOrder = {"name", "unit", "date", "time"})
    public interface TestBean extends FusionBean
    {
        @FoField(isKey = true, range = "1,20")
        String getName();

        TestBean setName(String value);

        int getInt();

        TestBean setInt(int value);

        short getShort();

        TestBean setShort(short value);

        long getLong();

        TestBean setLong(long value);

        @FoField(range = "0")
        int getPriority();

        TestBean setPriority(int value);

        @FoField(range = "\"2024-04-20\",null")
        LocalDate getDate();

        TestBean setDate(LocalDate value);

        @FoField(range = "\"00:00\", \"23:59\"")
        LocalTime getTime();

        TestBean setTime(LocalTime value);

        ChronoUnit getUnit();

        TestBean setUnit(ChronoUnit value);

        Blob getBlob();

        TestBean setBlob(Blob value);

        @FoField(itemType = Integer.class)
        List<Integer> getInts();

        TestBean setInts(List<Integer> value);

        @FoField(itemType = Long.class)
        Map<NoCaseString, Long> getLongMap();

        TestBean setLongMap(Map<NoCaseString, Long> value);

        @Override
        default void customPrepForIwrStateChange(IwrState targetState) {
            System.out.println("transitioning to " + targetState);
        }
    }
    @FoType
    interface TestBeanContainer extends FusionBean
    {
        TestBean bean();

        TestBeanContainer bean(TestBean value);
    }

    @FoType
    public static abstract class TestBean2 extends FusionBeanObject implements TestBean
    {
        private Long _computed;

        protected TestBean2(FusionBeanObjectType type) {
            super(type);
            setUnit(ChronoUnit.DAYS);
            /**
             * TODO: (Create task #TimePrecision with Fusion Task Manager (first group of L1 apps))
             * #TimePrecision.Task:
             * - support "precision" for LocalTime and Instant types
             * - embed the precision enum of "nanos" through "hours" inside FusionValueDomain.range
             * - the precision should be applied, via truncatedTo() method, when user sets field via 'set' method
             * - the precision should be verified when reading value from wire
             */
            setWhen(Instant.now().truncatedTo(ChronoUnit.SECONDS));
            setDate(LocalDate.now());
            setTime(LocalTime.now().truncatedTo(ChronoUnit.MINUTES));
            var bytes = "This is a test".getBytes(StandardCharsets.UTF_8);
            setBlob(new Blob(bytes));
            setInts(List.of(1, 2));
            setLongMap(Map.of(nocase("x"), 4L, nocase("y"), 20L));
        }

        protected TestBean2(TestBean2 copy) {
            super(copy);
        }

        public abstract Instant getWhen();

        protected abstract TestBean2 setWhen(Instant value);

        public Long getComputed() {
            return _computed;
        }

        @Override
        public void customPrepForIwrStateChange(IwrState targetState) {
            TestBean.super.customPrepForIwrStateChange(targetState);
            if (targetState.isReadonly()) {
                _computed = getLong() * getInt();
            }
        }
    }

    @BeforeAll
    static void init() {
        beanFactory = (FusionBeanObjectType<TestBean>) Fusion.fobType(TestBean.class);
        assertEquals(TestBean.class, beanFactory.javaDataClass());
        beanFactory2 = (FusionBeanObjectType<TestBean2>) Fusion.fobType(TestBean2.class);
        assertEquals(TestBean2.class, beanFactory2.javaDataClass());
        containerFactory = (FusionBeanObjectType<TestBeanContainer>) Fusion.fobType(TestBeanContainer.class);
    }
    TestBean beanA, keyA;
    TestBean2 bean2A, key2A;
    TestBeanContainer container;

    @BeforeEach
    void makeBeans() {
        beanA = beanFactory.make();
        keyA = beanFactory.makeKey();
        bean2A = beanFactory2.make();
        key2A = beanFactory2.makeKey();
        container = containerFactory.make();
    }

    @Test
    void findFobType() {
        assertNotNull(Fusion.fobType(FusionObjectSchema.class));
        assertNotNull(Fusion.fobType(FusionFieldSchema.class));
        assertNotNull(Fusion.fobType(FusionValueDomain.class));
    }

    @Test
    void beans() throws IOException {
        //var beanFactory = Fusion.generateBeanType(TestBean.class);
        System.out.println("new bean1: " + beanA);
        beanA.resetRaw();
        System.out.println("raw bean1: " + beanA);
        beanA.reset();
        System.out.println("new bean1: " + beanA);
        beanA.setInt(5).setShort((short) 10).setLong(20).setName("testing").setPriority(10);
        beanA.setBlob(new Blob("is this valid json string? No!".getBytes()));
        System.out.println("updated bean1: " + beanA);
        var bean1clone = (TestBean) beanA.cloneForWrite();
        assertEquals(beanA, bean1clone);
        bean1clone.setInt(6);
        assertNotEquals(beanA, bean1clone);
        beanA.setInt(6);
        assertEquals(beanA, bean1clone);
        System.out.println("bean1: " + beanA);
        System.out.println("bean1clone: " + bean1clone.toJsonString());
        System.out.println("bean1.blob2.bytes(utf-8): " + beanA.getBlob().as(StandardCharsets.UTF_8));

        bean2A = beanFactory2.make();
        bean2A.setName("bean2");
        bean2A.setLong(7);
        bean2A.setInt(11);
        System.out.println("bean2: " + bean2A);
        assertNull(bean2A.getComputed());
        var bean2clone = (TestBean2) bean2A.cloneForRead();
        System.out.println("bean2clone: " + bean2clone);
        assertEquals(bean2A, bean2clone);
        assertNotNull(bean2clone.getComputed());
        bean2A.doneWrite();
        assertNotNull(bean2A.getComputed());
        assertEquals(bean2A.getComputed(), 7 * 11);
        System.out.println("bean2.blob2.len=" + bean2A.getBlob().length());
        System.out.println("bean2.blob2.bytes(utf-8)=" + bean2A.getBlob().as(StandardCharsets.UTF_8));

        var bean3 = beanFactory2.read(
              "{\"name\":\"bean2\",\"unit\":\"DAYS\",\"date\":\"2024-05-01\",\"time\":\"11:06\"," +
              "\"blob\":\"VGhpcyBpcyBhIHRlc3Q\",\"int\":11,\"ints\":[1,2],\"long\":7,\"longMap\":{\"x\":4,\"y\":20}," +
              "\"priority\":0,\"short\":0,\"when\":\"2024-05-01T16:06:26Z\"}");

        System.out.println("bean3: " + bean3);

        //assertThrows(RuntimeException.class, () -> beanFactory1.makeKey());
        keyA = beanFactory.makeKey();
        assertThrows(RuntimeException.class, () -> keyA.setLong(0));
        assertThrows(RuntimeException.class, () -> keyA.ensureReadonly());
        System.out.println("new key1: " + keyA);
        assertThrows(IllegalArgumentException.class, () -> keyA.setName(""));
        assertDoesNotThrow(() -> keyA.setName("key1"));
        assertDoesNotThrow(() -> keyA.ensureReadonly());
        assertThrows(IllegalStateException.class, () -> keyA.setName("key1"));
        System.out.println("made key1: " + keyA);

        assertDoesNotThrow(() -> container.bean(beanA));
        assertDoesNotThrow(() -> container.bean(bean2A));
        assertThrows(IllegalArgumentException.class, () -> container.bean(keyA));
    }
}