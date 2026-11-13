package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.JavaMemberIndex;
import graal.mod.api.MemberRemapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Remaps host member names for the GraalJS layer. Delegates to {@link JavaMemberIndex#remapName},
 * passing {@code MemberRemapper.HIDE_MEMBER} as the hide marker. Returns
 * {@code MemberRemapper.FALL_THROUGH} when nothing matches, so a later remapper or Graal's
 * default rules still handle the member.
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
