package zer0g.fusion.data;

import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract non-sealed class FusionObjectTypeBase<T extends FusionObject> implements FusionObjectType<T>
{
    static final Method SCHEMA_METHOD;
    static final Method REGISTER_METHOD;

    static {
        try {
            REGISTER_METHOD = FusionObjectTypeBase.class.getDeclaredMethod("register");
            SCHEMA_METHOD = FusionObjectTypeBase.class.getMethod("schema");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    protected static FusionFieldSchema makeSchemaField(NoCaseString name, Method accessor, int index) {
        FusionValueDomain domain;
        FoField annotation = accessor.getAnnotation(FoField.class);
        if (accessor.getReturnType() == FusionValue.class) {
            domain = new FusionValueDomain(FusionValueType.ANY, null, FusionValue.NULL);
        } else {
            FusionValueType.QualifiedType qt = FusionValueType.typeForJavaValueClass(accessor.getReturnType(), true);
            FusionValue range = FusionValue.NULL;
            if (annotation != null) {
                if (qt.type() == FusionValueType.LIST || qt.type() == FusionValueType.MAP) {
                    var itemtype = annotation.itemType();
                    if (Object.class != itemtype) {
                        var elemqt = FusionValueType.typeForJavaValueClass(itemtype, true);
                        qt = new FusionValueType.QualifiedType(qt.type(), elemqt.toString());
                    }
                }
                if (!annotation.range().isEmpty()) {
                    try {
                        range = FusionValueType.LIST.read(new StringReader('[' + annotation.range() + ']'));
                    } catch (IOException e) {
                        throw new IllegalArgumentException("Invalid range: " + annotation.range());
                    }
                }
            }
            domain = new FusionValueDomain(qt.type(), qt.qualifier(), range);
        }
        int flags;
        if (null != annotation) {
            if (annotation.isKey()) {
                flags = FusionFieldSchema.KEY_FLAG | FusionFieldSchema.READONLY_FLAG;
            } else {
                flags = 0;
                if (annotation.isNullable()) {
                    flags |= FusionFieldSchema.NULLABLE_FLAG;
                }
                if (annotation.isReadonly()) {
                    flags |= FusionFieldSchema.READONLY_FLAG;
                }
            }
        } else {
            flags = FusionFieldSchema.NULLABLE_FLAG;
        }
        FusionValue defval;
        if (accessor.getReturnType().isPrimitive()) {
            flags = FusionFieldSchema.unsetFlag(flags, FusionFieldSchema.NULLABLE_FLAG);
            defval = domain.type().zero(domain);
        } else {
            defval = null;
        }
        if (null != annotation && !annotation.defval().isEmpty()) {
            try {
                defval = domain.type().readJson(annotation.defval(), domain);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid defval: " + annotation.defval(), e.getCause());
            }
        }
        return new FusionFieldSchema(name, domain, flags, defval, index);
    }

    protected static <T extends FusionObjectTypeBase> DynamicType.Builder<T> buildCtor(
          DynamicType.Builder<T> builder, FusionObjectSchema schema, Constructor<? extends FusionObjectType> superCtor)
    {
        var latentFieldsField = new FieldDescription.Latent(builder.toTypeDescription(),
                                                            "__fields",
                                                            Modifier.STATIC | Modifier.PRIVATE,
                                                            TypeDescription.Generic.Builder
                                                                  .parameterizedType(List.class,
                                                                                     FusionFieldSchema.class).build(),
                                                            null);
        var latentCtor = new MethodDescription.Latent(builder.toTypeDescription(), new MethodDescription.Token(0));
        builder = builder.define(latentFieldsField);
        builder = builder.invokable(ElementMatchers.isTypeInitializer())
                         .intercept(FusionObjectTypeBase.initFieldsField(latentFieldsField, schema.fields())
                                                        .andThen(MethodCall.construct(latentCtor)));
        builder = builder.defineConstructor(Visibility.PRIVATE).intercept(MethodCall.invoke(superCtor).onSuper()
                                                                                    .withMethodCall(MethodCall
                                                                                                          .construct(
                                                                                                                FusionObjectSchema.CANON_CTOR)
                                                                                                          .with(schema.id())
                                                                                                          .withField(
                                                                                                                latentFieldsField.getName()))
                                                                                    .andThen(MethodCall.invoke(
                                                                                          REGISTER_METHOD)));
        return builder;
    }

    private static Implementation.Composable initFieldsField(
          FieldDescription.Latent latentFieldsField, List<FusionFieldSchema> fields)
    {
        Implementation.Composable block = null;
        Method listAddMethod;
        try {
            block = MethodCall.construct(ArrayList.class.getDeclaredConstructor()).setsField(latentFieldsField);
            listAddMethod = List.class.getDeclaredMethod("add", Object.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        for (FusionFieldSchema field : fields) {
            block = block.andThen(MethodCall.invoke(listAddMethod).onField(latentFieldsField)
                                            .withMethodCall(FusionObjectTypeBase.makeNewFieldExpr(field)));
        }
        return block;
    }

    private static MethodCall makeNewFieldExpr(FusionFieldSchema field) {
        var defval = field.defval();
        return MethodCall.construct(FusionObjectSchema.FIELD_CTOR).with(field.name().toString())
                         .withMethodCall(FusionObjectTypeBase.makeNewValueDomainExpr(field.domain()))
                         .with(field.flags()).with(defval != null ? defval.toString() : null);
    }

    private static MethodCall makeNewValueDomainExpr(FusionValueDomain domain) {
        return MethodCall.construct(FusionObjectSchema.VALUE_DOMAIN_CTOR).with(domain.type()).with(domain.qualifier())
                         .withMethodCall(MethodCall.invoke(FusionObjectSchema.VALUE_READANY_METHOD)
                                                   .with(domain.range().toString()));
    }

    protected final FusionObjectSchema _schema;

    public FusionObjectTypeBase(FusionObjectSchema schema) {
        schema.fields().forEach(f -> validateFieldName(f.name()));
        _schema = Objects.requireNonNull(schema);
    }

    protected static void validateFieldName(NoCaseString fname) {
        if (!Character.isLetter(fname.charAt(0))) {
            throw new IllegalArgumentException("Invalid field name: " + fname);
        }
    }

    @Override
    public T makeKey() {
        if (schema()._keyFields().isEmpty()) {
            throw new UnsupportedOperationException("Fob-type has no key fields: " + name());
        }
        var o = make();
        ((FusionObjectBase) o)._isKey = true;
        return o;
    }

    @Override
    public final FusionObjectSchema schema() {
        return _schema;
    }

    @Override
    public final String name() {
        return _schema.id();
    }

    @Override
    public Validator<?> validatorFor(String algName) throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public final T read(Reader wire) throws IOException, ArithmeticException, ClassCastException {
        return (T) new JsonReader(wire).readObject(new FusionValueDomain(this)).get();
    }

    @Override
    public void write(Writer wire, FusionObject value) throws IOException {
        new JsonWriter(wire).visitObject(Objects.requireNonNull(value));
    }

    public final T read(String json) throws IOException {
        return read(new StringReader(json));

    }

    /**
     * Registers the fob-type.<p/> This method is called automatically when the fob-type class is constructed.
     */
    protected void register() {
        Fusion.registerFobType(this);
    }
}
