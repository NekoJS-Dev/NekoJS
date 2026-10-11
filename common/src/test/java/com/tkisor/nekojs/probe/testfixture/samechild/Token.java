package com.tkisor.nekojs.probe.testfixture.samechild;

public class Token extends com.tkisor.nekojs.probe.testfixture.samebase.Token {
    public Token(String value) { super(value); }
    public String acceptBase(com.tkisor.nekojs.probe.testfixture.samebase.Token value) { return value.baseValue(); }
}
