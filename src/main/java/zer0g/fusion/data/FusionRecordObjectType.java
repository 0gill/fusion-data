package zer0g.fusion.data;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static zer0g.fusion.data.NoCaseString.nocase;

public abstract class FusionRecordObjectType<T extends Record> extends FusionObjectTypeBase<FusionRecordObject<T>>
{
    static final Method SET_RECORD_METHOD;
    static final Method EXTRACT_RECORD_METHOD;
    static final Constructor<FusionRecordObjectType> CTOR;
    static final Method CLASS_FOR_NAME_METHOD;
    static final Method LIST_OF_METHOD;

    static {
        try {
            SET_RECORD_METHOD =
                  FusionRecordObjectType.class.getDeclaredMethod("_setRecord", FusionRecordObject.class, Record.class);
            EXTRACT_RECORD_METHOD =
                  FusionRecordObjectType.class.getDeclaredMethod("_extractRecord", FusionRecordObject.class);
            CTOR = FusionRecordObjectType.class.getDeclaredConstructor(FusionObjectSchema.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            CLASS_FOR_NAME_METHOD = Class.class.getDeclaredMethod("forName", String.class);
            LIST_OF_METHOD = List.class.getDeclaredMethod("of", Object[].class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static final class OnDemand<T extends Record> extends FusionRecordObjectType<T>
    {
        private final Class<T> _recordClass;
        private final Constructor<? extends Record> _recordCtor;

        OnDemand(Class<T> recordClass) {
            super(extractSchema(recordClass));
            _recordClass = recordClass;
            _recordCtor = canonCtor(recordClass);
            register();
        }

        @Override
        protected void _setRecord(FusionRecordObject<T> fo, T rec) {
            var components = _recordClass.getRecordComponents();
            for (FusionFieldSchema field : schema().fields()) {
                try {
                    fo.set(field._i(), components[field._i()].getAccessor().invoke(rec));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        protected T _extractRecord(FusionRecordObject<T> fo) {
            var args = new Object[schema().fields().size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = fo.get(i);
            }
            try {
                return (T) _recordCtor.newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static <T extends Record> FusionRecordObjectType<T> of(Class<T> recordClass) {
        var type = Fusion.findFobType(recordClass);
        if (null == type) {
            synchronized (FusionRecordObjectType.class) {
                type = Fusion.findFobType(recordClass);
                if (null == type) {
                    type = new OnDemand<>(recordClass);
                }
            }
        }
        return (FusionRecordObjectType<T>) type;
    }

    public static final void ensureLoaded(Class<? extends Record> recordClass) {
        try {
            var fobclass = Class.forName(Fusion.fobTypeClassNameFor(recordClass));
            // todo: log loading
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates using byte-buddy the fusion-object-type that will create {@link FusionRecordObject} adapters for the
     * specified java-record class.
     *
     * @param recClass
     *       the java-record's class
     * @return the byte-buddy dynamic-type that defines the fusion object-type for the supplied java-record class
     */
    public static DynamicType.Unloaded<FusionRecordObjectType> generateType(Class<? extends Record> recClass) {
        var superTypeDescription =
              TypeDescription.Generic.Builder.parameterizedType(FusionRecordObjectType.class, recClass).build();
        var builder = (DynamicType.Builder<FusionRecordObjectType>) new ByteBuddy().subclass(superTypeDescription,
                                                                                             ConstructorStrategy.Default.NO_CONSTRUCTORS)
                                                                                   .name(Fusion.fobTypeClassNameFor(
                                                                                         recClass));

        var components = persistentComponents(recClass);
        var schema = extractSchema(recClass, components);
        var canonCtor = canonCtor(recClass, components);

        MethodCall extractImpl = MethodCall.construct(canonCtor);
        MethodCall setCallChain = null;

        for (int i = 0; i < components.size(); i++) {
            RecordComponent component = components.get(i);
            Method getMethod;
            if (component.getType() == FusionValue.class) {
                getMethod = FusionObjectBase.GETFV_METHOD;
            } else {
                getMethod = FusionObjectBase.GET_METHOD;
            }
            extractImpl = (MethodCall) extractImpl.withMethodCall(MethodCall.invoke(getMethod).onArgument(0).with(i))
                                                  .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);

            var setCall = setCallChain == null
                          ? MethodCall.invoke(FusionObjectBase.SET_METHOD).onArgument(0)
                          : MethodCall.invoke(FusionObjectBase.SET_METHOD).onMethodCall(setCallChain);
            setCallChain = setCall.with(i).withMethodCall(MethodCall.invoke(component.getAccessor()).onArgument(1));
        }
        builder = builder.method(ElementMatchers.is(SET_RECORD_METHOD)).intercept(setCallChain);
        builder = builder.method(ElementMatchers.is(EXTRACT_RECORD_METHOD)).intercept(extractImpl);

        builder = buildCtor(builder, schema, CTOR);
        return builder.make();
    }

    static List<RecordComponent> persistentComponents(Class<? extends Record> recordClass) {
        RecordComponent[] components = recordClass.getRecordComponents();
        Integer underi = null;
        for (int i = 0; i < components.length; i++) {
            if (components[i].getName().startsWith("_")) {
                if (null == underi) {
                    underi = i;
                }
            } else if (null != underi) {
                throw new IllegalArgumentException(
                      "Visible component comes after hidden one(s): " + components[i].getName());
            }
        }
        List<RecordComponent> result;
        if (underi == null) {
            result = List.of(components);
        } else {
            result = List.of(Arrays.copyOf(components, underi.intValue()));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Record has 0 persistent components: " + recordClass.getName());
        }
        return result;
    }

    static FusionObjectSchema extractSchema(
          Class<? extends Record> recordClass, List<RecordComponent> components)
    {
        return new FusionObjectSchema(recordClass.getName(), FusionRecordObjectType.makeSchemaFields(components));
    }

    static <T extends Record> Constructor<T> canonCtor(
          Class<T> recordClass, List<RecordComponent> components)
    {
        try {
            Class<?>[] canonArgs = components.stream().map(RecordComponent::getType).toArray(Class<?>[]::new);
            return recordClass.getDeclaredConstructor(canonArgs);
        } catch (NoSuchMethodException e) {
            // todo: print canonArgs if exception below does not show signature desired
            throw new RuntimeException("No canonical ctor!", e);
        }
    }

    static List<FusionFieldSchema> makeSchemaFields(List<RecordComponent> clist) {
        List<FusionFieldSchema> fields = new ArrayList<>(clist.size());
        for (int i = 0; i < clist.size(); i++) {
            var component = clist.get(i);
            fields.add(makeSchemaField(nocase(component.getName()), component.getAccessor(), i));
        }
        return fields;
    }

    static <T extends Record> Constructor<T> canonCtor(Class<T> recordClass) {
        return canonCtor(recordClass, persistentComponents(recordClass));
    }

    static FusionObjectSchema extractSchema(Class<? extends Record> recordClass) {
        return FusionRecordObjectType.extractSchema(recordClass,
                                                    FusionRecordObjectType.persistentComponents(recordClass));
    }

    private final Class<T> _recordClass;

    protected FusionRecordObjectType(FusionObjectSchema schema) {
        super(schema);
        try {
            _recordClass = (Class<T>) Class.forName(name());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final Class javaDataClass() {
        return FusionRecordObject.class;
    }

    @Override
    public final FusionRecordObject<T> make() {
        return new FusionRecordObject(this);
    }

    final void setRecord(FusionRecordObject fo, T rec) {
        if (fo.type() != this) {
            throw new IllegalArgumentException("Wrong fo-type: " + fo.type());
        }
        if (rec.getClass() != javaRecordClass()) {
            throw new IllegalArgumentException("Wrong record class: " + rec.getClass());
        }
        _setRecord(fo, rec);
    }

    public final Class<T> javaRecordClass() {
        return _recordClass;
    }

    protected abstract void _setRecord(FusionRecordObject<T> fo, T rec);

    protected abstract T _extractRecord(FusionRecordObject<T> fo);
}
