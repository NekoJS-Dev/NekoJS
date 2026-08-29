package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.JavaMemberIndex;
import graal.mod.api.MemberRemapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GraalJS host 层成员名重映射：委托 {@link JavaMemberIndex#remapName}，hideMarker 传
 * {@code MemberRemapper.HIDE_MEMBER}，未命中时返回 {@code MemberRemapper.FALL_THROUGH}，
 * 让后续 remapper 或 Graal 默认规则继续处理。
 *
 * @author ZZZank
 */
public class NekoJSMemberRemapper implements MemberRemapper {

    @Override
    public String remapField(Field field, Class<?> exposedClass) {
        return JavaMemberIndex.remapName(field, MemberRemapper.HIDE_MEMBER, MemberRemapper.FALL_THROUGH);
    }

    @Override
    public String remapMethod(Method method, Class<?> exposedClass) {
        return JavaMemberIndex.remapName(method, MemberRemapper.HIDE_MEMBER, MemberRemapper.FALL_THROUGH);
    }
}
