package com.tkisor.nekojs.probe.testfixture.samechild;

public class CallbackUser {
    public com.tkisor.nekojs.probe.testfixture.samebase.Token invokeBase(
            com.tkisor.nekojs.probe.testfixture.samebase.Callback callback,
            com.tkisor.nekojs.probe.testfixture.samebase.Token value) { return callback.apply(value); }

    public com.tkisor.nekojs.probe.testfixture.samebase.Token invokeLocal(
            Callback callback, com.tkisor.nekojs.probe.testfixture.samebase.Token value) { return callback.apply(value); }
}
