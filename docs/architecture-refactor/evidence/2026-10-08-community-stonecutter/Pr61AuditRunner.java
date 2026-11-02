package com.tkisor.nekojs.core.compiler;
import java.lang.reflect.*;
public class Pr61AuditRunner {
    private static int passed, failed;
    private static void check(String name, Runnable run) {
        try { run.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) {
            failed++;
            Throwable cause = error instanceof RuntimeException && error.getCause() != null ? error.getCause() : error;
            System.out.println("FAIL " + name + " | " + cause);
        }
    }
    public static void main(String[] args) throws Exception {
        var test = new Pr61CorpusAuditTest();
        var method = java.util.Arrays.stream(Pr61CorpusAuditTest.class.getDeclaredMethods())
            .filter(m -> m.getName().equals("erasedOutputParsesAndKeepsLineCount")).findFirst().orElseThrow();
        ((java.util.stream.Stream<?>)Pr61CorpusAuditTest.corpus()).forEach(c -> check(c.toString().split(", source=")[0], () -> {
            try { method.invoke(test, c); }
            catch (InvocationTargetException error) { throw new RuntimeException(error.getCause()); }
            catch (Exception error) { throw new RuntimeException(error); }
        }));
        Pr61CorpusAuditTest.rejectedSyntax().forEach(a -> check("rejects:" + a.get()[0], () ->
            test.unsupportedSyntaxFailsWithActionableMessage((String)a.get()[0], (String)a.get()[1])));
        check("switchAndLabelSurviveBehaviorally", test::switchAndLabelSurviveBehaviorally);
        check("enumFloatValueSurvivesBehaviorally", test::enumFloatValueSurvivesBehaviorally);
        System.out.println("AUDIT_TOTAL passed=" + passed + " failed=" + failed + " skipped=0");
    }
}