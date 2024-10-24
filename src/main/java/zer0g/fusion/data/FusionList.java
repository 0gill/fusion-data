package zer0g.fusion.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class FusionList<T> extends InitWriteReadStateData.Base implements List<T>
{
    final List<FusionValue> _inner;
    final FusionValueDomain _domain;

    FusionList(Collection<?> fromUser, FusionValueDomain domain) {
        this(domain);
        fromUser.stream().map(v -> wrap(v)).forEach(_inner::add);
    }

    public FusionList(FusionValueDomain domain) {
        if (domain.type() != FusionValueType.LIST) {
            throw new IllegalArgumentException("" + domain.type());
        }
        _domain = domain;
        _inner = new ArrayList<>();
    }

    private FusionValue wrap(Object javaVal) {
        return valueDomain().type().from(javaVal, valueDomain());
    }

    public FusionValueDomain domain() {
        return _domain;
    }

    public FusionValueDomain valueDomain() {
        return _domain._itemDomain();
    }

    @Override
    public <T1> T1[] toArray(IntFunction<T1[]> generator) {
        return stream().toArray(generator);
    }

    @Override
    public boolean removeIf(Predicate<? super T> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Stream<T> stream() {
        return List.super.stream();
    }

    @Override
    public Stream<T> parallelStream() {
        return List.super.parallelStream();
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        List.super.forEach(action);
    }

    @Override
    public int hashCode() {
        state().requireReadonly();
        return Objects.hash(domain()._itemDomain().type(), _inner.size());
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || obj.getClass() != getClass()) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        FusionList olist = (FusionList) obj;
        if (size() == olist.size() && domain()._itemDomain().type() == olist.domain()._itemDomain().type()) {
            for (int i = 0; i < inner().size(); i++) {
                if (!inner().get(i).equals(olist.inner().get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
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
    public boolean contains(Object o) {
        var ofv = wrap(o);
        return inner().stream().anyMatch(ofv::equals);
    }

    @Override
    public Iterator<T> iterator() {
        return new DuaListIterator(this);
    }

    @Override
    public Object[] toArray() {
        return stream().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return stream().toArray((size) -> a.length >= size ? a : Arrays.copyOf(a, size));
    }

    @Override
    public boolean add(T element) {
        state().requireWritable();
        if (!_inner.add(wrap(element))) {
            throw new AssertionError();
        }
        return true;
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(this::contains);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        c.stream().forEach(this::add);
        return !c.isEmpty();
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        for (T item : c) {
            add(index++, item);
        }
        return !c.isEmpty();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(Comparator<? super T> c) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void clear() {
        state().requireWritable();
        _inner.clear();
    }

    @Override
    public T get(int index) {
        return (T) inner().get(index).getinner();
    }

    @Override
    public T set(int index, T element) {
        state().requireWritable();
        var old = _inner.set(index, wrap(element));
        return old != null ? (T) old.get() : null;
    }

    @Override
    public void add(int index, T element) {
        state().requireWritable();
        _inner.add(index, wrap(element));
    }

    @Override
    public T remove(int index) {
        state().requireWritable();
        return (T) _inner.remove(index).get();
    }

    @Override
    public int indexOf(Object o) {
        var ofv = wrap(o);
        int i = 0;
        for (var fv : inner()) {
            if (fv.equals(ofv)) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        var ofv = wrap(o);
        int i = 0;
        for (var fv : inner().reversed()) {
            if (fv.equals(ofv)) {
                return inner().size() - i - 1;
            }
            ++i;
        }
        return -1;
    }

    @Override
    public ListIterator<T> listIterator() {
        return new DuaListIterator(this);
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        return new DuaListIterator<>(this, index);
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return new FusionList<>(inner().subList(fromIndex, toIndex), domain());
    }

    @Override
    public Spliterator<T> spliterator() {
        return List.super.spliterator();
    }

    @Override
    public List<T> reversed() {
        // TODO: super won't return a FusionList
        return List.super.reversed();
    }

    List<FusionValue> inner() {
        return _inner;
    }

    @Override
    protected void prepForIwrStateChange(IwrState nextState) {
        if (!state().isInit()) {
            // We CANNOT set _inner to an unmobifiable-list because it is final.  We do NOT need to do so anyway.
        }
    }

    private class DuaListIterator<T> implements ListIterator<T>
    {
        final ListIterator<? extends FusionValue> _innerIter;

        DuaListIterator(FusionList<T> fusionList, int index) {
            _innerIter = fusionList.inner().listIterator(index);
        }

        public <T> DuaListIterator(FusionList<T> fusionList) {
            _innerIter = fusionList.inner().listIterator();
        }

        @Override
        public boolean hasNext() {
            return _innerIter.hasNext();
        }

        @Override
        public T next() {
            return (T) _innerIter.next().get();
        }

        @Override
        public boolean hasPrevious() {
            return _innerIter.hasPrevious();
        }

        @Override
        public T previous() {
            return (T) _innerIter.previous().get();
        }

        @Override
        public int nextIndex() {
            return _innerIter.nextIndex();
        }

        @Override
        public int previousIndex() {
            return _innerIter.previousIndex();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(T t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(T t) {
            throw new UnsupportedOperationException();
        }
    }
}
