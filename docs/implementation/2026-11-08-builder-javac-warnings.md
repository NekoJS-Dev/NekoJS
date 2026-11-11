# Builder javac warning corrections

## Scope

- Replace the two unchecked shader-map getter casts in `PostEffectBuilder` with checked entry casts into an immutable, detached map. The schema remains the sole write validator; unset reads still do not add declaration fields.
- Create the `ItemModificationJS` proxy surface on first use rather than passing `this` out of a field initializer. Apply the same correction to the 1.21.1 source split. Existing property/setter dispatch remains unchanged.
- Create the three `FluidBuilder` sibling builders together on first use rather than invoking overridable ID methods in the superclass constructor. Preserve custom IDs, lazy native object creation, sibling supplier identity, registration order, and block/bucket suppression. Apply the same correction to the 1.21.1 source split.
- No warning suppression or public signature changes were added. Derived fluid IDs are now read after normal subclass construction, at first use rather than in the superclass constructor.

## Regression evidence

- The new fluid initialization test failed before the fix: an overridden ID getter observed its subclass field as null while `FluidBuilder.<init>` was running.
- The corrected test verifies initialized custom IDs, stable sibling supplier identity across repeated registration, and block/bucket suppression without constructing native objects.
- The shader-map test covers unset defaults, detached Java input, immutable read snapshots, guest writes, and agreement with finished declaration data. Existing modification property/setter parity tests cover proxy delegation.
- Targeted checks passed: `:26.1.2:test --tests com.tkisor.nekojs.wrapper.registry.gen.FluidBuilderInitializationTest --tests com.tkisor.nekojs.client.posteffect.PostEffectBuilderTest --tests com.tkisor.nekojs.wrapper.event.server.ModificationSetterPropertyParityTest guardLint`. The four reported production javac warnings no longer appeared.

## Verification status

- The first full matrix run exposed a new test-scope mistake: the shader-map getter regression also compiled against the intentionally different 1.21.1 shader API. Restrict that regression to 26.x with the existing version guard mechanism; do not add unsupported legacy methods.
- Full builds passed: `./gradlew :1.21.1:build :26.1.2:build :26.2.0:build :26.1.2-fabric:build :26.2.0-fabric:build guardLint --no-daemon`. Node test results were respectively 478, 848, 848, 443, and 443 tests, all with zero failures; `guardLint` reported 425 guards across 628 files with zero warnings.
- Source-root checks passed for all five nodes: `python tools/nekojs-ci-gates.py source-roots --node <node>`. All CI gates passed: `python tools/nekojs-ci-gates.py all --out build/nekojs-gates-report.json`.
- Scoped whitespace review: passed.
- Unrelated existing test-code and third-party JDK warnings remain outside this correction.
- No live game run or maintainer acceptance was performed for this correction.
