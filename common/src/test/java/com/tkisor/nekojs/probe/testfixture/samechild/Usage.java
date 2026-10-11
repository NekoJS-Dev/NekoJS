package com.tkisor.nekojs.probe.testfixture.samechild;

public class Usage {
    public PersistentData self;
    public com.tkisor.nekojs.probe.testfixture.samebase.PersistentData<String> base;
    public com.tkisor.nekojs.probe.testfixture.sameother.PersistentData other;

    public com.tkisor.nekojs.probe.testfixture.samebase.PersistentData<String> acceptBase(
            com.tkisor.nekojs.probe.testfixture.samebase.PersistentData<String> value) { return value; }

    public com.tkisor.nekojs.probe.testfixture.sameother.PersistentData acceptOther(
            com.tkisor.nekojs.probe.testfixture.sameother.PersistentData value) { return value; }
}
