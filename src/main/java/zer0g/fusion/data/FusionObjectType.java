package zer0g.fusion.data;

public sealed interface FusionObjectType<T extends FusionObject> extends FusionDataType<T>
      permits FobTypeGenerator.TypeBeingGenerated, FusionObjectTypeBase
{
    @Override
    Class<? extends T> javaDataClass();

    /**
     * Creates a new fob with the primitive [non-nullable] types set to their defaults. Creates a new "raw" fob with all
     * values set to null; hence primitive field getters will throw exception.
     *
     * @return
     */
    T make();

    /**
     * Creates a "key" fob with all fields set to null.  Only the "key" fields can be written to.
     * @return a raw fob whose {@link FusionObject#isKey()} is true; null if there are no key fields in the schema.
     */
    T makeKey();

    /**
     * @return the schema that defines the allowed fields, and their value domains.
     */
    FusionObjectSchema schema();
}
