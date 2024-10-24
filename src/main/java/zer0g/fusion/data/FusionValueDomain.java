package zer0g.fusion.data;

import java.util.List;
import java.util.Objects;

/**
 * @param type
 * @param qualifier
 *       a type-specific keyword to "qualify" the type further
 * @param range
 *       a special validation that is applicable across all types, albeit with different semantics.
 */
@FoType
public record FusionValueDomain(FusionValueType type, @FoField(isNullable = true) String qualifier, FusionValue range,
                                FusionValueType.MinMaxRange _compiledRange, FusionValueDomain _itemDomain,
                                FusionObjectType _fobType, Class<?> _javaDataClass)
{
    public static final FusionValueDomain ANY = new FusionValueDomain(FusionValueType.ANY);
    public static final FusionValueDomain LIST_ANY = new FusionValueDomain(FusionValueType.LIST);
    public static final FusionValueDomain MAP_ANY = new FusionValueDomain(FusionValueType.MAP);

    public FusionValueDomain(FusionValueType type) {
        this(type, null);
    }

    public FusionValueDomain(FusionValueType type, String qualifier) {
        this(type, qualifier, FusionValue.NULL);
    }

    public FusionValueDomain(FusionValueType type, String qualifier, FusionValue range) {
        this(type, qualifier, range, null, null, null, null);
    }

    public FusionValueDomain {
        Objects.requireNonNull(type);
        Objects.requireNonNull(range);
        if (null != _compiledRange) {
            throw new IllegalArgumentException("_compiledRange must be null!");
        }
        if (null != _itemDomain) {
            throw new IllegalArgumentException("_itemDomain must be null!");
        }
        if (null != _fobType) {
            throw new IllegalArgumentException("_fobType must be null!");
        }
        if (null != _javaDataClass) {
            throw new IllegalArgumentException("_javaDataClass must be null!");
        }
        if (!range.isNull()) {
            _compiledRange = type.compileRange(range, qualifier);
        }
        switch (type) {
            case OBJECT -> {
                _fobType = Fusion.fobType(qualifier);
            }
            default -> {
                _javaDataClass = type.javaDataClass(qualifier);       // throws exception if bad qualifier
            }
        }
        switch (type) {
            case LIST, MAP -> {
                if (null == qualifier) {
                    _itemDomain = FusionValueDomain.ANY;
                } else {
                    var itemqt = FusionValueType.QualifiedType.fromString(qualifier);
                    switch (itemqt.type()) {
                        case LIST, MAP ->
                              throw new IllegalArgumentException(itemqt.type() + " cannot be nested in " + type);
                    }
                    FusionValue itemRange = FusionValue.NULL;
                    if (!range.isNull()) {
                        FusionList<?> list = (FusionList<?>) range.get();
                        if (list.size() >= 3) {
                            itemRange = list.inner().get(2);
                        }
                    }
                    _itemDomain = new FusionValueDomain(itemqt, itemRange);
                }
            }
        }
    }

    public FusionValueDomain(FusionValueType.QualifiedType qt) {
        this(qt, FusionValue.NULL);
    }

    public FusionValueDomain(FusionValueType.QualifiedType qt, FusionValue range) {
        this(qt.type(), qt.qualifier(), range);
    }

    public FusionValueDomain(FusionObjectType fotype) {
        this(FusionValueType.OBJECT, fotype.name());
    }

    public void validateRange(FusionValue fv, List<String> collect) throws FusionDataType.ValidationException {
        if (_compiledRange != null) {
            type.validateRange(_compiledRange, fv.get());
        }
    }
}
