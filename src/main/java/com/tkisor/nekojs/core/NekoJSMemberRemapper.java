package com.tkisor.nekojs.core;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import graal.mod.api.MemberRemapper;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * @author ZZZank
 */
public class NekoJSMemberRemapper implements MemberRemapper {

    @Override
    public String remapField(Field field) {
        return remapImpl(field);
    }

    @Override
    public String remapMethod(Method method) {
        return remapImpl(method);
    }

    private static <T extends AccessibleObject & Member> String remapImpl(T member) {
        var remap = member.getAnnotation(Remap.class);
        if (remap != null) {
            return remap.value();
        }

        var original = member.getName();

        var remapByPrefix = member.getAnnotation(RemapByPrefix.class);
        if (remapByPrefix != null) {
            var remapped = findAndRemovePrefix(original, remapByPrefix.value());
            if (remapped != null) {
                return remapped;
            }
        }

        remapByPrefix = member.getDeclaringClass().getAnnotation(RemapByPrefix.class);
        if (remapByPrefix != null) {
            var remapped = findAndRemovePrefix(original, remapByPrefix.value());
            if (remapped != null) {
                return remapped;
            }
        }

        return original;
    }

    private static String findAndRemovePrefix(String name, String[] prefixes) {
        for (var prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return null;
    }
}
