package zer0g.fusion.data;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public final class IwrList<T> extends InitWriteReadStateData.Base implements List<T>
{
    private final List<T> _inner;

    public IwrList(Class<? extends List> type)
    {
        try {
            _inner = type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        doneInit();
    }

    @Override
    protected void prepForIwrStateChange(IwrState nextState) {
    }

    @Override
    public int size() {
        return _inner.size();
    }

    @Override
    public boolean isEmpty() {
        return _inner.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return _inner.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>()
        {
            private int _i = 0;

            @Override
            public boolean hasNext() {
                return _i < size();
            }

            @Override
            public T next() {
                return get(_i++);
            }

            @Override
            public void remove() {
                state().requireWritable();
                IwrList.this.remove(_i);
            }
        };
    }

    @Override
    public Object[] toArray() {
        return _inner.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return _inner.toArray(a);
    }

    @Override
    public boolean add(T t) {
        state().requireWritable();
        return _inner.add(t);
    }

    @Override
    public boolean remove(Object o) {
        state().requireWritable();
        return _inner.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return _inner.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        state().requireWritable();
        return _inner.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        state().requireWritable();
        return _inner.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        state().requireWritable();
        return _inner.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        state().requireWritable();
        return _inner.retainAll(c);
    }

    @Override
    public void clear() {
        state().requireWritable();
        _inner.clear();
    }

    @Override
    public T get(int index) {
        return _inner.get(index);
    }

    @Override
    public T set(int index, T element) {
        state().requireWritable();
        return _inner.set(index, element);
    }

    @Override
    public void add(int index, T element) {
        state().requireWritable();
        _inner.add(index, element);
    }

    @Override
    public T remove(int index) {
        state().requireWritable();
        return _inner.remove(index);
    }

    @Override
    public int indexOf(Object o) {
        return _inner.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return _inner.lastIndexOf(o);
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new RuntimeException("todo");
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new RuntimeException("todo");
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        return Collections.unmodifiableList(_inner.subList(fromIndex, toIndex));
    }
}
