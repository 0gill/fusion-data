package zer0g.fusion.data;

public non-sealed abstract class FusionBeanObject extends FusionObjectBase implements FusionBean
{
    protected FusionBeanObject(FusionBeanObjectType type) {
        super(type);
    }

    protected FusionBeanObject(FusionBeanObject copy) {
        super(copy);
    }

    @Override
    protected final void prepForIwrStateChange(IwrState nextState) {
        super.prepForIwrStateChange(nextState);
        customPrepForIwrStateChange(nextState);
    }
}
