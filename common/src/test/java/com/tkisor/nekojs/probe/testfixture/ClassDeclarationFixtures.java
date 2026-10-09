package com.tkisor.nekojs.probe.testfixture;

public final class ClassDeclarationFixtures {
    private ClassDeclarationFixtures() {
    }

    public static final class Imported {
        public String dependencyMethod() {
            return "dependency";
        }
    }

    public static final class Adapted {
        public String adaptedMethod() {
            return "adapted";
        }
    }
}
