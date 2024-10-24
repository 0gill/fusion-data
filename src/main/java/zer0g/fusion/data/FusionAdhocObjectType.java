package zer0g.fusion.data;

public final class FusionAdhocObjectType extends FusionObjectTypeBase<FusionObjectBase.Adhoc>
{
    public FusionAdhocObjectType(FusionObjectSchema schema) {
        super(schema);
    }

    @Override
    public Class<FusionObjectBase.Adhoc> javaDataClass() {
        return FusionObjectBase.Adhoc.class;
    }

    @Override
    public FusionObjectBase.Adhoc make() {
        return new FusionObjectBase.Adhoc(this);
    }
}
