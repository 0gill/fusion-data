package zer0g.fusion.data;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

final class FusionMap<T> extends InitWriteReadStateData.Base implements Map<NoCaseString, T>
{
    final Map<NoCaseString, FusionValue> _inner = new TreeMap<>();
    final FusionValueDomain _domain;

    FusionMap(Map<NoCaseString, T> fromUser, FusionValueDomain domain) {
        this(domain);
        for (var entry : fromUser.entrySet()) {
            _inner.put(entry.getKey(), valueDomain().type().from(entry.getValue(), valueDomain()));
        }
    }

    public FusionMap(FusionValueDomain domain) {
        if (domain.type() != FusionValueType.MAP) {
            throw new IllegalArgumentException("" + domain.type());
        }
        assert (domain._itemDomain() != null);
        _domain = domain;
    }

    public FusionValueDomain valueDomain() {
        return _domain._itemDomain();
    }

    public FusionValueDomain domain() {
        return _domain;
    }

    @Override
    public int hashCode() {
        state().requireReadonly();
        return Objects.hash(valueDomain().type(), _inner.size());
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || obj.getClass() != getClass()) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        FusionMap omap = (FusionMap) obj;
        if (size() == omap.size() && valueDomain().type() == omap.valueDomain().type()) {
            for (var key : keySet()) {
                if (!inner().get(key).equals(omap.inner().get(key))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected FusionMap<T> clone() {
        throw new RuntimeException("todo");
    }

    @Override
    public int size() {
        return inner().size();
    }

    @Override
    public boolean isEmpty() {
        return inner().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return inner().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        var ofv = valueDomain().type().from(value, null);
        for (FusionValue fv : inner().values()) {
            if (fv.equals(ofv)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public T get(Object key) {
        return (T) inner().get(key).getinner();
    }

    @Override
    public synchronized T put(NoCaseString key, T value) {
        state().requireWritable();
        var oldfv = inner().put(key, valueDomain().type().from(value, valueDomain()));
        return oldfv != null ? (T) oldfv.getinner() : null;
    }

    @Override
    public T remove(Object o) {
        state().requireWritable();
        throw new RuntimeException("todo");
    }

    @Override
    public void putAll(Map<? extends NoCaseString, ? extends T> m) {
        state().requireWritable();
        throw new RuntimeException("todo");
    }

    @Override
    public void clear() {
        state().requireWritable();
        inner().clear();
    }

    @Override
    public Set<NoCaseString> keySet() {
        return inner().keySet();
    }

    @Override
    public Collection<T> values() {
        throw new RuntimeException("todo");
    }

    @Override
    public Set<Entry<NoCaseString, T>> entrySet() {
        throw new RuntimeException("todo");
    }

    Map<NoCaseString, FusionValue> inner() {
        return _inner;
    }

    @Override
    protected void prepForIwrStateChange(IwrState nextState) {
        if (!state().isInit()) {
            // We CANNOT set _inner to an unmobifiable-list because it is final.  We do NOT need to do so anyway.
        }
    }

    private FusionValue wrap(Object javaVal) {
        return valueDomain().type().from(javaVal, valueDomain());
    }
}
