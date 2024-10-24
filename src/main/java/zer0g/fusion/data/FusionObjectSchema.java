package zer0g.fusion.data;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Note that a record is not allowed to have instance fields; all of record's fields are declared as parameters in its
 * definition.  Lazily/on-demand computed fields can be implemented by adding custom public getter method to a record.
 * But what if we want to "cache" the results of a non-trivial computation?  We cannot store them in an instance
 * field... but, we CAN store the results in one of the record's declared fields WHEN the record is constructed
 * (eager-cache).  Lazy-caching is possible by using AtomicReference type for the declared field.
 *
 * @param id
 * @param fields
 * @param _fieldMap
 *       mapping of field names to the field records, generated.  Must pass in null.
 * @param _keyFields
 *       list of key field records, generated.  Must pass in null.
 */
@FoType
public record FusionObjectSchema(String id, @FoField(itemType = FusionFieldSchema.class) List<FusionFieldSchema> fields,
                                 Map<NoCaseString, FusionFieldSchema> _fieldMap, List<FusionFieldSchema> _keyFields)
{
    static final Constructor<FusionObjectSchema> CANON_CTOR;
    static final Constructor<FusionFieldSchema> FIELD_CTOR;
    static final Constructor<FusionValueDomain> VALUE_DOMAIN_CTOR;
    static final Method VALUE_READANY_METHOD;

    static {
        try {
            CANON_CTOR = FusionObjectSchema.class.getDeclaredConstructor(String.class, List.class);
            FIELD_CTOR = FusionFieldSchema.class.getDeclaredConstructor(String.class,
                                                                        FusionValueDomain.class,
                                                                        int.class,
                                                                        String.class);
            VALUE_DOMAIN_CTOR = FusionValueDomain.class.getDeclaredConstructor(FusionValueType.class,
                                                                               String.class,
                                                                               FusionValue.class);
            VALUE_READANY_METHOD = FusionValueType.class.getMethod("readAny", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public FusionObjectSchema(String id, List<FusionFieldSchema> fields) {
        this(id, fields, null, null);
    }

    public FusionObjectSchema {
        Objects.requireNonNull(id);
        Objects.requireNonNull(fields);
        if (_fieldMap != null) {
            throw new IllegalArgumentException("Specified field-map MUST be null!");
        }
        if (_keyFields != null) {
            throw new IllegalArgumentException("Specified key-fields MUST be null!");
        }
        if (fields.isEmpty()) {
            fields = List.of();
            _fieldMap = Map.of();
            _keyFields = List.of();
        } else {
            FusionFieldSchema[] indexed = new FusionFieldSchema[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                indexed[i] = new FusionFieldSchema(fields.get(i), i);
            }
            fields = List.of(indexed);
            _fieldMap = new LinkedHashMap<>(fields.size());
            int i = 0;
            for (FusionFieldSchema field : fields) {
                var old = _fieldMap.put(field.name(), field);
                if (old != null) {
                    throw new IllegalArgumentException("More than one field with name: " + field.name());
                }
            }
            _fieldMap = Collections.unmodifiableMap(_fieldMap);
            _keyFields = fields.stream().filter(FusionFieldSchema::isKey).toList();
        }
    }

    public FusionFieldSchema field(NoCaseString name) {
        var f = findField(name);
        if (null == f) {
            throw new IllegalArgumentException("No such field: " + name);
        }
        return f;
    }

    public FusionFieldSchema findField(NoCaseString name) {
        return _fieldMap.get(name);
    }

    @Override
    public String toString() {
        return id;
    }

}