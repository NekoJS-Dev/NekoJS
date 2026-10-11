package com.tkisor.nekojs.probe.testfixture.samechild;

public class PersistentData extends com.tkisor.nekojs.probe.testfixture.samebase.PersistentData<String> {
    public PersistentData(String value) { super(value); }

    public String acceptBase(com.tkisor.nekojs.probe.testfixture.samebase.PersistentData<String> value) {
        return value.baseValue();
    }
}
