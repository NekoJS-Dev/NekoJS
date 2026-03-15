package com.tkisor.nekojs.api.event;

import com.tkisor.nekojs.script.ScriptType;

public interface IScriptHandler {
    /**
     * 获取脚本类型
     * @return 脚本类型对象
     */
    ScriptType scriptType();
}