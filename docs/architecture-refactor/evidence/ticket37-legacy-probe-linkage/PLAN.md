# Dedicated-server Probe reflection diagnosis

Baseline source0d06a6fb; installed legacy dedicated-server variant boots, but both `/nekojs probe typescript` and `/nekojs probe python` fail with missing `net.minecraft.client.model.Model` at shared `ProbeClassCollector.getDeclaredMethods`. This independent failure is preserved in the legacy ICU evidence. No permissions, include defaults or public deletion gates may be weakened.

## Feedback loop and hypotheses

The original deterministic loop is the owned server runner plus `prove-other-nodes.py` selecting1.21.1. Both backend commands already reproduce the same shared collection error. First tighten the loop with a missing-signature class-loader fixture and a server profile without custom registration scripts; preserve the original profile and logs.

Ranked hypotheses before further instrumentation:

1. A reachable host signature enters a loader client extension whose own signatures reference absent client-only classes. Prediction: contextual reflection failure names that extension, while the class itself loads without initialization; a class-loader fixture with one missing signature reproduces the shared failure.
2. The dedicated-server catalog directly registers a client event or Binding. Prediction: the failing type is a depth-zero seed and correcting catalog availability removes the failure without changing closure handling.
3. A static initializer fails when Probe reflects an otherwise available type. Prediction: initialization-disabled loading succeeds but initialized loading fails; failure stack contains class initialization rather than only method signature resolution.

Temporary instrumentation must identify only the failed class/depth, retain the cause, and be removed before delivery. Then select the narrow shared fix from the observed evidence. Best-effort degradation must remain explicit in generation results; no missing declaration or complete IDE result may be described as a full PASS. Existing IR best-effort behavior is relevant but does not authorize silently swallowing arbitrary errors.
