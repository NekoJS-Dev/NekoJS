package com.tkisor.nekojs.script;

import java.util.function.Predicate;

/**
 * @author ZZZank
 */
public interface ScriptTypeFilter extends Predicate<ScriptType> {

    @Override
    boolean test(ScriptType scriptType);
}
