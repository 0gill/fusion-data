package zer0g.fusion.data;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FoField
{
    boolean isKey() default false;

    boolean isReadonly() default false;

    boolean isNullable() default true;

    Class<?> itemType() default Object.class;

    String range() default "";

    String defval() default "";
}
