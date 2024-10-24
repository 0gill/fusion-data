package zer0g.fusion.data;

import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public sealed class FusionObjectBase extends InitWriteReadStateData.Base implements FusionObject
      permits FusionBeanObject, FusionRecordObject, FusionObjectBase.Adhoc
{

    protected static final Method GET_METHOD;
    protected static final Method SET_METHOD;
    protected static final Method GETFV_METHOD;
    protected static final Method SETFV_METHOD;

    static {
        try {
            GET_METHOD = FusionObjectBase.class.getMethod("get", int.class);
            SET_METHOD = FusionObjectBase.class.getDeclaredMethod("set", int.class, Object.class);
            GETFV_METHOD = FusionObjectBase.class.getDeclaredMethod("getfv", int.class);
            SETFV_METHOD = FusionObjectBase.class.getDeclaredMethod("setfv", int.class, FusionValue.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static final class Adhoc extends FusionObjectBase
    {
        Adhoc(FusionAdhocObjectType type) {
            super(type);
        }
    }
    /**
     * Set by {@link FusionObjectTypeBase#makeKey()}
     */
    boolean _isKey;
    protected final FusionObjectTypeBase _type;
    protected final ArrayList<FusionValue> _valList;

    protected FusionObjectBase(FusionObjectTypeBase type) {
        _type = Objects.requireNonNull(type);
        var fields = type.schema().fields();
        _valList = new ArrayList<>(fields.size());
        for (FusionFieldSchema field : fields) {
            _valList.add(field.defaultValue());
        }
    }

    protected FusionObjectBase(FusionObjectBase copy) {
        _type = copy._type;
        _isKey = copy._isKey;
        _valList = new ArrayList<>(copy._valList);
    }

    @Override
    public FusionObjectBase clone(IwrState wantedState) {
        return (FusionObjectBase) super.clone(wantedState);
    }

    @Override
    protected void prepForIwrStateChange(IwrState nextState) {
        assert Thread.holdsLock(this);
        // Does not matter if next state is WRITE or READ, ALL* fields must be null and range validated.
        // If key-fob, then ALL* means all key-fields.
        var fields = isKey() ? schema()._keyFields() : schema().fields();
        List<String> errors = new LinkedList<>();
        for (FusionFieldSchema field : fields) {
            var fv = getfv(field._i());
            if (fv.isNull()) {
                if (!field.isNullable()) {
                    throw new NullPointerException(type().name() + ": Field " + field.name() + " cannot be null!");
                }
            } else {
                field.domain().validateRange(fv, errors);
            }
        }

    }

    @Override
    public boolean isKey() {
        return _isKey;
    }

    @Override
    public final FusionValue getfv(NoCaseString fieldName) {
        return getfv(indexOf(fieldName));
    }

    @Override
    public final FusionObjectBase set(int i, Object value) {
        state().requireWritable();

        FusionFieldSchema field = schema().fields().get(i);
        assert field._i() == i;
        if (isKey() && !field.isKey()) {
            throw new IllegalStateException("Field " + field.name() + " is NOT a key!");
        }
        if (state().isWrite()) {
            if (field.isReadonly()) {
                throw new IllegalStateException("Field " + field.name() + " is readonly!");
            }
            if (null == value && !field.isNullable()) {
                throw new IllegalStateException("Field " + field.name() + " is not nullable!");
            }
        }
        try {
            return setfv(i, field.domain().type().from(value, field.domain()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for field " + field.name(), e);
        }
    }

    @Override
    public final int indexOf(NoCaseString fieldName) {
        return schema().field(fieldName)._i();
    }

    @Override
    public Set<NoCaseString> fieldNames() {
        return schema()._fieldMap().keySet();
    }

    @Override
    public boolean has(NoCaseString fieldName) {
        return null != schema().findField(fieldName);
    }

    @Override
    public FusionObjectType type() {
        return _type;
    }

    @Override
    public final Map<NoCaseString, FusionValue> asMap() {
        return new AbstractMap<>()
        {
            @Override
            public int size() {
                return _valList.size();
            }

            @Override
            public boolean containsValue(Object value) {
                return _valList.contains(value);
            }

            @Override
            public boolean containsKey(Object key) {
                return null != schema().findField((NoCaseString) key);
            }

            @Override
            public FusionValue get(Object key) {
                return getfv((NoCaseString) key);
            }

            @Override
            public FusionValue put(NoCaseString key, FusionValue value) {
                var field = schema().field(key);
                var old = getfv(field._i());
                set(field._i(), value);
                return old;
            }

            @Override
            public FusionValue remove(Object key) {
                return put((NoCaseString) key, FusionValue.NULL);
            }

            @Override
            public synchronized void clear() {
                state().requireWritable();
                _valList.replaceAll(item -> FusionValue.NULL);
            }

            @Override
            public Set<Entry<NoCaseString, FusionValue>> entrySet() {
                return new AbstractSet<>()
                {
                    @Override
                    public Iterator<Entry<NoCaseString, FusionValue>> iterator() {
                        return new Iterator<>()
                        {
                            int i = 0;

                            @Override
                            public boolean hasNext() {
                                return i < _valList.size();
                            }

                            @Override
                            public Entry<NoCaseString, FusionValue> next() {
                                if (hasNext()) {
                                    var entry =
                                          new SimpleEntry<NoCaseString, FusionValue>(schema().fields().get(i).name(),
                                                                                     _valList.get(i));
                                    ++i;
                                    return entry;
                                } else {
                                    throw new NoSuchElementException();
                                }
                            }
                        };
                    }

                    @Override
                    public int size() {
                        return _valList.size();
                    }
                };
            }
        };
    }

    @Override
    public final FusionValue getfv(int i) {
        return _valList.get(i);
    }

    @Override
    public void resetRaw() throws IllegalStateException {
        reset((f) -> FusionValue.NULL);
    }

    @Override
    public void reset() {
        reset((f) -> f.defaultValue());
    }

    private void reset(Function<FusionFieldSchema, FusionValue> func) {
        reset(switch (state()) {
            case INIT -> schema().fields().stream();
            case WRITE -> schema().fields().stream().filter(Predicate.not(FusionFieldSchema::isReadonly));
            case READ -> throw new AssertionError();
        }, func);
    }

    private void reset(Stream<FusionFieldSchema> fields, Function<FusionFieldSchema, FusionValue> func) {
        state().requireWritable();
        fields.forEach(f -> _valList.set(f._i(), func.apply(f)));
    }

    protected final synchronized FusionObjectBase setfv(int i, FusionValue value) {
        state().requireWritable();

        _valList.set(i, value);
        return this;
    }

    @Override
    public FusionObjectBase cloneForWrite() throws FusionDataType.ValidationException {
        return (FusionObjectBase) super.cloneForWrite();
    }

    @Override
    public FusionObjectBase cloneForInit() {
        return (FusionObjectBase) super.cloneForInit();
    }

    @Override
    public FusionObjectBase cloneForRead() throws FusionDataType.ValidationException {
        return (FusionObjectBase) super.cloneForRead();
    }

    @Override
    public int hashCode() {
        if (null != schema() && !schema()._keyFields().isEmpty()) {
            return getfv(schema()._keyFields().get(0).name()).hashCode();
        }
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || obj.getClass() != getClass()) {
            return false;
        }
        var ofo = (FusionObject) obj;
        if (ofo.type() != type()) {
            return false;
        }
        var fields = (this.isKey() || ofo.isKey()) ? schema()._keyFields() : schema().fields();
        for (FusionFieldSchema field : fields) {
            if (!getfv(field._i()).equals(ofo.getfv(field._i()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return
     */
    @Override
    public String toString() {
        return toJsonString();
    }
}
