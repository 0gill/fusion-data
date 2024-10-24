package zer0g.fusion.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FoType
{
    /**
     * Some, all, or none of the fields in an annotated {@link FusionBean} sub-interface or {@link FusionBeanObject}
     * subclass. The fields specified here, if any, are put at the start, in the specified order.  The fields not
     * specified here, if any, are sorted by their name and added at the end.
     *
     * @return
     */
    String[] fieldOrder() default {};
}
