package zer0g.fusion.data;

public interface InitWriteReadStateData
{
    enum IwrState
    {
        /**
         * Just-created state in which object is being initialized. User expects to set/modify ANY field on the object.
         */
        INIT,
        /**
         * Interim state.  Only some (possibly all) fields can be modified.
         */
        WRITE,
        /**
         * Final state.  Object is readonly.
         */
        READ;

        public final void requireWritable() {
            if (isReadonly()) {
                throw new IllegalStateException("Not writable!");
            }
        }

        /**
         * @return true if this is {@link #READ}
         */
        public final boolean isReadonly() {
            return this == READ;
        }

        public final void requireWrite() {
            if (!isWrite()) {
                throw new IllegalStateException("Not write!");
            }
        }

        public final boolean isWrite() {
            return this == WRITE;
        }

        public final void requireReadonly() {
            if (!isReadonly()) {
                throw new IllegalStateException("Not readonly!");
            }
        }

        public final void requireInit() {
            if (!isInit()) {
                throw new IllegalStateException("Non-init state (" + this + ")!");
            }
        }

        /**
         * @return true if this is {@link #INIT}
         */
        public final boolean isInit() {
            return this == INIT;
        }
    }

    abstract class Base implements InitWriteReadStateData, Cloneable
    {
        private IwrState _state = IwrState.INIT;

        @Override

        public final synchronized Base doneInit() throws FusionDataType.ValidationException {
            switch (_state) {
                case INIT -> trans(IwrState.WRITE);
                case READ, WRITE -> throw new IllegalStateException("Already done-init!");
            }
            return this;
        }

        @Override
        public synchronized Base ensureReadonly() throws FusionDataType.ValidationException {
            return (Base) InitWriteReadStateData.super.ensureReadonly();
        }

        @Override
        public final synchronized IwrState state() {
            return _state;
        }

        @Override
        public final synchronized Base doneWrite() throws FusionDataType.ValidationException {
            switch (_state) {
                case INIT, WRITE -> trans(IwrState.READ);
                case READ -> throw new IllegalStateException("Not writable!");
            }
            return this;
        }

        @Override
        public synchronized Base clone(IwrState wantedState) {
            try {
                Base clone;
                if (state() == wantedState) {
                    if (state().isReadonly()) {
                        clone = this;
                    } else {
                        clone = (Base) clone();
                    }
                } else {
                    clone = (Base) clone();
                    switch (wantedState) {
                        case READ -> clone.doneWrite();
                        case WRITE -> {
                            if (clone._state.isInit()) {
                                clone.doneInit();
                            } else {
                                assert clone._state.isReadonly();
                                clone._state = IwrState.WRITE;
                            }
                        }
                        case INIT -> clone._state = IwrState.INIT;
                    }
                }
                assert clone.state() == wantedState;
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        private void trans(IwrState next) {
            prepForIwrStateChange(next);
            _state = next;
        }

        protected abstract void prepForIwrStateChange(IwrState nextState);
    }

    /**
     * Transitions object from {@link IwrState#INIT} to {@link IwrState#WRITE}.
     *
     * @return
     */
    InitWriteReadStateData doneInit() throws FusionDataType.ValidationException;

    default InitWriteReadStateData ensureReadonly() throws FusionDataType.ValidationException {
        if (!state().isReadonly()) {
            doneWrite();
        }
        return this;
    }

    IwrState state();

    /**
     * Transitions object from {@link IwrState#INIT} or {@link IwrState#WRITE} to {@link IwrState#READ}.
     *
     * @return
     */
    InitWriteReadStateData doneWrite() throws FusionDataType.ValidationException;

    default InitWriteReadStateData cloneForWrite() throws FusionDataType.ValidationException {
        return clone(IwrState.WRITE);
    }

    InitWriteReadStateData clone(IwrState wantedState) throws FusionDataType.ValidationException;

    default InitWriteReadStateData cloneForInit() {
        try {
            return clone(IwrState.INIT);
        } catch (FusionDataType.ValidationException e) {
            throw new AssertionError();
        }
    }

    default InitWriteReadStateData cloneForRead() throws FusionDataType.ValidationException {
        return clone(IwrState.READ);
    }
}
