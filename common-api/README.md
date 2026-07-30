# NekoJS Common API

This module owns Java contracts that may form NekoJS's long-lived JS and
addon API. It contains stable-shape interfaces, value and contract models,
lifecycle annotations, and Graal-free conversion abstractions.

Types in this module must not depend on Minecraft, a loader, Graal, or NekoJS
runtime implementation packages. The `checkApiBoundaries` Gradle task enforces
that rule and is part of `check`.

Physical placement here does not by itself freeze a type into API 1.0. The
normative API contract and compatibility gates remain the source of truth for
stability. Graal proxies, event dispatch implementations, plugin bootstrap,
Probe generation, and preview host APIs stay in `common` until a stable facade
or SPI contract is defined.
