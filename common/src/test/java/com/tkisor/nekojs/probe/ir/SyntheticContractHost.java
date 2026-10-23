package com.tkisor.nekojs.probe.ir;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.probe.ir.TypeReflectorSyntheticContractTest.Box;
import com.tkisor.nekojs.probe.ir.TypeReflectorSyntheticContractTest.Values;

/** Test host whose selected method flags are transformed to model injected accessors. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SyntheticContractHost implements Values<String>, Comparable<SyntheticContractHost> {
    public Box getValues() { return new Box("owned"); }
    @Remap("ownLabel")
    public String alias() { return "label"; }
    @HideFromJS
    public String secret() { return "secret"; }
    public String noise() { return "internal"; }
    public int compareTo(SyntheticContractHost other) { return 0; }
}
