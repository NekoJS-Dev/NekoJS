# SAM signature linkage recovery review

Fixed point: `8922627e75f903fb8efbbbf7ceb89dd51b1f50d0`. Scope: FunctionalInterfaceAliasGenerator, ProbeLinkageFixture and ProbeUnavailableTypeTest only. Unrelated generated declaration status noise is excluded. Reviews were read-only; neither reviewer ran tests or certified overall tickets.

## Standards

Independent agent `sam_linkage_standards`: no documented violations or actionable design findings. Per-interface recovery matches the existing shared Probe contract. Imports are staged before alias publication; VirtualMachineError and ThreadDeath remain uncaught. The warning identifies the affected interface and outcome using NEKO-4029 and attaches the cause. New comments/messages are English. Class-loader regression checks exercise actual missing/rejected signature dependencies through the coordinator, verify surviving output and both backend warnings, and restore the context loader in finally. No new API/dependency/loader coupling or generated-output patch.

## Spec

Independent agent `sam_linkage_spec`: no findings. RuntimeException/LinkageError recovery restores partial legacy output with explicit alias omission. Signature dependencies are collected before registration. Manual declarations, HostAccess, ClassFilter, supported platforms and runtime conversion contracts are preserved. Missing/rejected signatures and fatal OutOfMemoryError are covered. The patch directly addresses the real getMethods -> resolver -> prepare stack without scope expansion.

Findings: Standards 0; Spec 0. These source reviews do not replace the full matrix, installed artifacts, strict TypeScript/Pyright or maintainer acceptance.
