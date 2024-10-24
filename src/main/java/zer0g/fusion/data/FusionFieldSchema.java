package zer0g.fusion.data;

import java.io.IOException;

/**
 * @param name
 * @param domain
 * @param flags
 *       a mask indicating whether the field is a key, readonly, and/or is nullable.
 * @param defval
 * @param _i
 *       transient/computed 0-based index of a field in the object-schema
 */
@FoType
public record FusionFieldSchema(NoCaseString name, FusionValueDomain domain, int flags, FusionValue defval, int _i)
{
    public static final int KEY_FLAG = 0x01;
    public static final int READONLY_FLAG = 0x02;
    public static final int NULLABLE_FLAG = 0x04;
    private static final int NULL_i = -1;

    public static int setFlag(int flags, int flag) {
        return flags | flag;
    }

    public static int unsetFlag(int flags, int flag) {
        // Note: (flags ^ flag) only works if flag is not currently set... otherwise it ends up setting it!
        // Alt: (flags | flag) ^ flag
        // Alt: isFlagSet(flags,flag) ? flags^flag : flags
        return flags & (~0 ^ flag);
    }

    public FusionFieldSchema(NoCaseString name, FusionValueDomain domain, int flags) {
        this(name, domain, flags, null);
    }

    public FusionFieldSchema(NoCaseString name, FusionValueDomain domain, int flags, FusionValue defval) {
        this(name, domain, flags, defval, NULL_i);
    }

    public FusionFieldSchema {
        if (isKey()) {
            if (isNullable()) {
                throw new IllegalArgumentException("Key field cannot be nullable!");
            }
            if (!isReadonly()) {
                throw new IllegalArgumentException("Key field MUST be readonly!");
            }
        }
        if (null != defval && !defval.isNull()) {
            if (domain.type() != defval.type()) {
                throw new IllegalArgumentException("Wrong default value type: " + defval.type());
            }
            if (domain._javaDataClass() != defval.get().getClass()) {
                throw new IllegalArgumentException("Wrong default value class: " + defval.get().getClass());
            }
        }
    }

    public boolean isKey() {
        return isFlagSet(flags, KEY_FLAG);
    }

    public boolean isNullable() {
        return isFlagSet(flags, NULLABLE_FLAG);
    }

    public boolean isReadonly() {
        return isFlagSet(flags, READONLY_FLAG);
    }

    public static boolean isFlagSet(int flags, int flag) {
        return 0 != (flags & flag);
    }

    public FusionFieldSchema(String name, FusionValueDomain domain, int flags, String defvalJson) throws IOException {
        this(name, domain, flags, defvalJson, NULL_i);
    }

    public FusionFieldSchema(String name, FusionValueDomain domain, int flags, String defvalJson, int _i)
          throws IOException
    {
        this(new NoCaseString(name), domain, flags, domain.type().readJson(defvalJson, domain), _i);
    }

    FusionFieldSchema(FusionFieldSchema copy, int index) {
        this(copy.name, copy.domain, copy.flags, copy.defval, index);
    }

    public FusionValue defaultValue() {
        return defval != null ? defval : FusionValue.NULL;
    }

    public FusionValue zero() {
        return domain.type().zero(domain);
    }
}
