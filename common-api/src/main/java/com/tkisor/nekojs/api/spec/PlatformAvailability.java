package com.tkisor.nekojs.api.spec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 spec 接口或方法的平台可用性。
 *
 * <p>标注在 spec 接口上表示该 spec 涵盖的平台范围。SpecCoverageProcessor 读取此注解
 * 决定校验哪些平台实现。
 *
 * <p>标注在单个方法上表示该方法的可用性（用于脱离 spec 单独使用时的文档化）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PlatformAvailability {
    /** 该 spec / 方法的目标平台范围。 */
    Scope value() default Scope.ALL;

    /**
     * 平台范围枚举。
     *
     * <p>{@link #ALL} 意味着所有目标平台（NF26 / NF121 / CR / 未来 Fabric）都必须实现该 spec。
     * 只有"所有平台原生类都不提供、需 mixin 注入"的方法才应进入 {@code ALL} spec。
     */
    enum Scope {
        /** 所有目标平台必须实现（NF26 / NF121 / CR / Future Fabric） */
        ALL,
        /** 仅 NeoForge 系列（26.x + 1.21.1） */
        NF_ONLY,
        /** 仅 Cleanroom 1.12.2 */
        CR_ONLY
    }
}
