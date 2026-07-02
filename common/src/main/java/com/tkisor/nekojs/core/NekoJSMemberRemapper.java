package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.JavaMemberIndex;
import graal.mod.api.MemberRemapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GraalJS host 层成员名重映射：委托 {@link JavaMemberIndex#remapName}，hideMarker 传
 * {@code MemberRemapper.HIDE_MEMBER}（满足 graal.mod.api SPI 约定），strict=false（前缀剥离允许空名）。
 *
 * @author ZZZank
 */
public class NekoJSMemberRemapper implements MemberRemapper {

    @Override
    public String remapField(Field field) {
        return JavaMemberIndex.remapName(field, MemberRemapper.HIDE_MEMBER, false);
    }

    @Override
    public String remapMethod(Method method) {
        return JavaMemberIndex.remapName(method, MemberRemapper.HIDE_MEMBER, false);
    }
}
