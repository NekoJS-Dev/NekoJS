package com.tkisor.nekojs.api.testing;

/**
 * Detached outcome of one TEST entry/reload attempt and its existing timer-drain window.
 * Completion does not certify callbacks scheduled beyond that window or future event handlers.
 * Counts are deltas for this attempt, not lifetime totals of the shared Test binding.
 */
public record TestRunReport(boolean executionCompleted, boolean executionSucceeded, int passed, int failed) {
    public TestRunReport {
        if (passed < 0 || failed < 0) throw new IllegalArgumentException("Assertion counts must be non-negative");
        if (executionSucceeded && !executionCompleted) {
            throw new IllegalArgumentException("An unfinished TEST attempt cannot have succeeded");
        }
    }

    /** Null means no assertions were observed; it is not a vacuous assertion pass. */
    public Boolean assertionsPassed() {
        if (passed == 0 && failed == 0) return null;
        return failed == 0;
    }

    /** Execution and at least one assertion must both succeed for an overall pass. */
    public boolean overallPassed() {
        return executionSucceeded && Boolean.TRUE.equals(assertionsPassed());
    }
}
