package com.omni.procurement.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明采购方法所使用的完整数据范围权限码。
 *
 * @author Omni-Stack Team
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProcDataScope {

    /**
     * 与方法权限校验一致的完整权限码。
     *
     * @return 权限码
     */
    String permissionCode();
}
