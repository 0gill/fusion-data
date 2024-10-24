package zer0g.fusion.data;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class FusionRecordObject<T extends Record> extends FusionObjectBase
{

    public static <T extends Record> FusionRecordObject<T> from(T rec) {
        return ((FusionRecordObject<T>) of(rec.getClass())).set(rec);
    }

    public FusionRecordObject<T> set(T rec) {
        state().requireWritable();
        type().setRecord(this, rec);
        return this;
    }

    public static <T extends Record> FusionRecordObject<T> of(Class<T> recordClass) {
        return FusionRecordObjectType.of(recordClass).make();
    }

    @Override
    public FusionRecordObjectType<T> type() {
        return (FusionRecordObjectType<T>) super.type();
    }

    private final AtomicReference<T> _recRef = new AtomicReference<>();

    public FusionRecordObject(FusionRecordObject<T> copy) {
        super(copy);
    }

    private FusionRecordObject(FusionRecordObjectType fobType, T rec) {
        super(fobType);
        _recRef.set(rec);
        type().setRecord(this, rec);
        doneWrite();
    }

    FusionRecordObject(FusionRecordObjectType<T> fobType) {
        super(fobType);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(_recRef.get());
    }

    public T extract() {
        if (state().isReadonly()) {
            if (_recRef.get() == null) {
                synchronized (this) {
                    if (_recRef.get() == null) {
                        _recRef.set(type()._extractRecord(this));
                    }
                }
            }
            return _recRef.get();
        } else {
            return type()._extractRecord(this);
        }
    }

    @Override
    public FusionRecordObject<T> clone() {
        return new FusionRecordObject<T>(this);
    }

    @Override
    protected void prepForIwrStateChange(IwrState nextState) {
        assert Thread.holdsLock(this);
        // We could eagerly extract the record and set it in _recRef, but we
        // do it on demand in extract()
    }
}
