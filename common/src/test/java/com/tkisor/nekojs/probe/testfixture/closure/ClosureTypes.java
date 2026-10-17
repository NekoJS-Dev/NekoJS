package com.tkisor.nekojs.probe.testfixture.closure;

public final class ClosureTypes {
    private ClosureTypes() {}

    public static class Parent { public int token; }
    public static class Child { public String text; }
    public static class Leaf extends Parent { public Child nested; }
}
