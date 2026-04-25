package com.guatai.yangaicodemother.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ClassName: auth
 * Package: com.guatai.yangaicodemother.annotation
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午4:07
 * @Version 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     */
    String mustRole() default "";

}

