package zer0g.fusion.data;

import net.bytebuddy.dynamic.DynamicType;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Fusion
{
    public static final String FOB_TYPE_CLASS_NAME_SUFFIX = "_FobType";
    private static final Map<String, FusionObjectType> _typeMap = new ConcurrentHashMap<>();

    public static final FusionObjectType findFobType(Class<?> javaClass) {
        return findFobType(javaClass.getName());
    }

    public static final FusionObjectType findFobType(String id) {
        return _typeMap.get(id);
    }

    public static final void generateFobType(Class<?> javaClass, File dir) throws IOException {
        makeFobType(javaClass).saveIn(dir.getAbsoluteFile());
    }

    private static DynamicType.Unloaded<? extends FusionObjectType> makeFobType(Class<?> javaClass) {
        DynamicType.Unloaded<? extends FusionObjectType> bbdt;
        if (javaClass.isRecord()) {
            bbdt = FusionRecordObjectType.generateType((Class<? extends Record>) javaClass);
        } else if (FusionBean.class.isAssignableFrom(javaClass)) {
            bbdt = FusionBeanObjectType.generateType((Class<FusionBean>) javaClass);
        } else {
            throw new IllegalArgumentException(
                  "Class not a record or FusionBean sub-interface: " + javaClass.getName());
        }
        System.out.println("Generated fusion-object-type for " + javaClass.getName());
        return bbdt;
    }

    public static String fobTypeClassBaseNameFor(Class<?> javaClass) {
        var base = javaClass.getName();
        int lastdot = base.lastIndexOf('.');
        if (lastdot != -1) {
            base = base.substring(lastdot + 1);
        }
        return base + FOB_TYPE_CLASS_NAME_SUFFIX;
    }

    public static final FusionObjectType fobType(Class<?> javaClass) {
        return fobType(javaClass.getName());
    }

    public static final FusionObjectType fobType(String id) {
        var t = findFobType(id);
        if (t == null) {
            try {
                Class.forName(id + FOB_TYPE_CLASS_NAME_SUFFIX);
                t = findFobType(id);
                if (t != null) {
                    return t;
                }
            } catch (ClassNotFoundException e) {
            }
            throw new IllegalArgumentException(id);
        }
        return t;
    }

    public static String fobTypeClassNameFor(Class<?> javaClass) {
        return javaClass.getName() + FOB_TYPE_CLASS_NAME_SUFFIX;
    }

    /**
     * This package-private method is automatically called by the every generated fob-type's static initializer.
     *
     * @param base
     */
    synchronized static void registerFobType(FusionObjectType base) {
        if (_typeMap.containsKey(base.name())) {
            throw new IllegalArgumentException("Object-type already registered: " + base.name());
        }
        _typeMap.put(base.name(), base);
    }
}
