package zer0g.fusion.data;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;

import java.beans.Introspector;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static zer0g.fusion.data.NoCaseString.nocase;

public abstract class FusionBeanObjectType<T extends FusionBean> extends FusionObjectTypeBase<T>
{
    static final Method MAKE_NEW_METHOD;
    static final Method JAVA_DATA_CLASS;
    static final Constructor<FusionBeanObjectType> CTOR;
    protected static final Constructor<FusionBeanObject> BASEBEAN_CLONE_CTOR;
    protected static final Constructor<FusionBeanObject> BASEBEAN_CTOR;
    protected static final Method CLONE_METHOD;

    static {
        try {
            MAKE_NEW_METHOD = FusionBeanObjectType.class.getDeclaredMethod("make");
            JAVA_DATA_CLASS = FusionBeanObjectType.class.getDeclaredMethod("javaDataClass");
            CTOR = FusionBeanObjectType.class.getDeclaredConstructor(FusionObjectSchema.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            BASEBEAN_CLONE_CTOR = FusionBeanObject.class.getDeclaredConstructor(FusionBeanObject.class);
            BASEBEAN_CTOR = FusionBeanObject.class.getDeclaredConstructor(FusionBeanObjectType.class);
            CLONE_METHOD = Object.class.getDeclaredMethod("clone");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static DynamicType.Unloaded<FusionBeanObjectType> generateType(Class<? extends FusionBean> beanClass) {
        if (beanClass == FusionBean.class || beanClass == FusionBeanObject.class) {
            throw new IllegalArgumentException(beanClass.getName());
        }
        if (!beanClass.isInterface() && !FusionBeanObject.class.isAssignableFrom(beanClass)) {
            throw new IllegalArgumentException(beanClass.getName());
        }

        var getters = new LinkedHashMap<NoCaseString, Method>();
        var setters = new HashMap<NoCaseString, Method>();
        List<Class<?>> interfaces = new ArrayList<>();
        hierarchy(beanClass, interfaces);
        enum NamingStyle
        {
            BEAN,
            RECORD
        }
        NamingStyle namingStyle = null;
        for (Class<?> beanOrSuper : interfaces) {
            var curgetters = new HashMap<NoCaseString, Method>();
            for (Method method : beanOrSuper.getDeclaredMethods()) {
                if (!Modifier.isAbstract(method.getModifiers())) {
                    continue;
                }
                var mname = method.getName();
                if (null == namingStyle || namingStyle == NamingStyle.BEAN) {
                    if (mname.length() >= 4 && (mname.startsWith("get") || mname.startsWith("set"))) {
                        var fname = nocase(Introspector.decapitalize(mname.substring(3)));
                        validateFieldName(fname);
                        if (mname.startsWith("get")) {
                            if (method.getParameterCount() != 0) {
                                throw new IllegalArgumentException("Invalid get method: " + method);
                            }
                            // todo: check return is not void
                            addFieldMethod(curgetters, fname, method);
                        } else {
                            if (method.getParameterCount() != 1) {
                                throw new IllegalArgumentException("Invalid set method (args>1): " + method);
                            }
                            expectSetterReturnType(method);
                            addFieldMethod(setters, fname, method);
                        }
                        namingStyle = NamingStyle.BEAN;
                    } else if (null != namingStyle) {
                        throw new IllegalArgumentException(
                              "Unexpected method (expected-style= " + namingStyle + "): " + method);
                    }
                }
                if (null == namingStyle || namingStyle == NamingStyle.RECORD) {
                    var fname = nocase(mname);
                    if (method.getParameterCount() == 0) {
                        addFieldMethod(curgetters, fname, method);
                    } else if (method.getParameterCount() == 1) {
                        expectSetterReturnType(method);
                        addFieldMethod(setters, fname, method);
                    } else {
                        throw new IllegalArgumentException(
                              "Unexpected method (expected-style= " + namingStyle + "): " + method);
                    }
                    validateFieldName(fname);
                    namingStyle = NamingStyle.RECORD;
                }
            }

            var typeAnnotation = beanOrSuper.getAnnotation(FoType.class);
            if (null != typeAnnotation) {
                for (String key : typeAnnotation.fieldOrder()) {
                    var nocasekey = nocase(key);
                    var getter = curgetters.remove(nocasekey);
                    if (null == getter) {
                        throw new IllegalArgumentException("Invalid field in field-order: " + key);
                    }
                    addFieldMethod(getters, nocasekey, getter);
                }
            }
            curgetters.keySet().stream().sorted().forEach(key -> getters.put(key, curgetters.get(key)));
        }
        for (NoCaseString getkey : getters.keySet()) {
            var getter = getters.get(getkey);
            var setter = setters.get(getkey);
            if (null == setter) {
                throw new IllegalArgumentException("Missing setter for " + getkey);
            }
            if (!setter.getParameterTypes()[0].equals(getter.getReturnType())) {
                throw new IllegalArgumentException("Getter-return & setter-param mismatch: " + getter + ", " + setter);
            }
        }
        for (NoCaseString setkey : setters.keySet()) {
            if (!getters.containsKey(setkey)) {
                throw new IllegalArgumentException("Missing getter for " + setkey);
            }
        }

        ////
        // NOTE: Due to keys in setters and getters map being no-case, the setter and getter methods MAY DIFFER in
        // case yet map to the same field. :-)
        ////

        assert setters.size() == getters.size();

        if (getters.isEmpty()) {
            throw new IllegalArgumentException("There are no getters/setters: " + beanClass.getName());
        }

        var superTypeDescription =
              TypeDescription.Generic.Builder.parameterizedType(FusionBeanObjectType.class, beanClass).build();
        var builder = (DynamicType.Builder<FusionBeanObjectType>) new ByteBuddy().subclass(superTypeDescription,
                                                                                           ConstructorStrategy.Default.NO_CONSTRUCTORS)
                                                                                 .name(Fusion.fobTypeClassNameFor(
                                                                                       beanClass));
        DynamicType.Builder<FusionBeanObject> beanBuilder;
        Constructor<FusionBeanObject> superBeanCtor;
        Constructor<FusionBeanObject> superBeanCloneCtor;
        if (beanClass.isInterface()) {
            beanBuilder = new ByteBuddy().subclass(FusionBeanObject.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                                         .implement(beanClass);
            superBeanCtor = BASEBEAN_CTOR;
            superBeanCloneCtor = BASEBEAN_CLONE_CTOR;
        } else {
            beanBuilder = (DynamicType.Builder<FusionBeanObject>) new ByteBuddy().subclass(beanClass,
                                                                                           ConstructorStrategy.Default.NO_CONSTRUCTORS);
            try {
                superBeanCtor =
                      (Constructor<FusionBeanObject>) beanClass.getDeclaredConstructor(FusionBeanObjectType.class);
                superBeanCloneCtor = (Constructor<FusionBeanObject>) beanClass.getDeclaredConstructor(beanClass);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        beanBuilder = beanBuilder.modifiers(Modifier.STATIC | Modifier.PRIVATE)
                                 .name(builder.toTypeDescription().getName() + "$Bean")
                                 .innerTypeOf(builder.toTypeDescription()).asMemberType();

        builder = builder.declaredTypes(beanBuilder.toTypeDescription());
        List<FusionFieldSchema> fields = new ArrayList<>(getters.size());
        for (NoCaseString key : getters.keySet()) {
            MethodCall impl;
            var getter = getters.get(key);
            var setter = setters.get(key);
            int i = fields.size();
            Method getMethod, setMethod;
            if (getter.getReturnType() == FusionValue.class) {
                getMethod = FusionObjectBase.GETFV_METHOD;
                setMethod = FusionObjectBase.SETFV_METHOD;
            } else {
                getMethod = FusionObjectBase.GET_METHOD;
                setMethod = FusionObjectBase.SET_METHOD;
            }
            beanBuilder = beanBuilder.method(ElementMatchers.is(getter)).intercept(MethodCall.invoke(getMethod).with(i)
                                                                                             .withAssigner(Assigner.DEFAULT,
                                                                                                           Assigner.Typing.DYNAMIC));
            beanBuilder = beanBuilder.method(ElementMatchers.is(setter))
                                     .intercept(MethodCall.invoke(setMethod).with(i).withArgument(0)
                                                          .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC));

            try {
                fields.add(makeSchemaField(key, getter, i));
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate schema for field " + key + " from accessor: " + getter,
                                           e);
            }
        }

        beanBuilder = beanBuilder.defineConstructor(Visibility.PRIVATE).withParameters(beanBuilder.toTypeDescription())
                                 .intercept(MethodCall.invoke(superBeanCloneCtor).onSuper().withAllArguments());
        beanBuilder =
              beanBuilder.defineConstructor(Visibility.PACKAGE_PRIVATE).withParameters(FusionBeanObjectType.class)
                         .intercept(MethodCall.invoke(superBeanCtor).onSuper().withAllArguments());
        MethodDescription beanCtor = null;
        MethodDescription cloneCtor = null;
        for (MethodDescription.InDefinedShape declaredMethod : beanBuilder.toTypeDescription().getDeclaredMethods()) {
            if (declaredMethod.isConstructor()) {
                if (declaredMethod.getParameters().get(0).getType().equals(beanBuilder.toTypeDescription())) {
                    cloneCtor = declaredMethod;
                } else {
                    beanCtor = declaredMethod;
                }
            }
        }
        beanBuilder = beanBuilder.method(ElementMatchers.is(CLONE_METHOD))
                                 .intercept(MethodCall.construct(cloneCtor).withThis());
        builder =
              builder.method(ElementMatchers.is(MAKE_NEW_METHOD)).intercept(MethodCall.construct(beanCtor).withThis());
        builder = builder.method(ElementMatchers.is(JAVA_DATA_CLASS)).intercept(FixedValue.value(beanClass));
        var schema = new FusionObjectSchema(beanClass.getName(), fields);
        builder = buildCtor(builder, schema, CTOR);
        return builder.make().include(beanBuilder.make());
    }

    private static void addFieldMethod(HashMap<NoCaseString, Method> map, NoCaseString fname, Method method) {
        var old = map.put(fname, method);
        if (old != null) {
            throw new IllegalArgumentException(
                  "Duplicate methods for field '" + fname + "': " + method.getName() + ", " + old.getName());
        }
    }

    private static void expectSetterReturnType(Method method) {
        if (method.getReturnType() != void.class &&
            !method.getReturnType().isAssignableFrom(method.getDeclaringClass()))
        {
            throw new IllegalArgumentException(
                  "Invalid set method (return-type not void or " + method.getDeclaringClass().getName() + "): " +
                  method);
        }
    }

    private static void hierarchy(Class<?> beanOrSuper, List<Class<?>> list) {
        if (beanOrSuper == FusionBean.class || beanOrSuper == FusionBeanObject.class) {
            return;
        }
        for (Class<?> beanSuper : beanOrSuper.getInterfaces()) {
            hierarchy(beanSuper, list);
        }
        list.add(beanOrSuper);
    }

    protected FusionBeanObjectType(FusionObjectSchema schema) {
        super(schema);
    }

    @Override
    abstract public Class<T> javaDataClass();

    @Override
    abstract public T make();
}
