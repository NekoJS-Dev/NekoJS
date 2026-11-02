# World line and box rendering on Minecraft 26.2

## Scope and root causes

The shared 26.x [WorldRenderContextJS](../../src/main/java/com/tkisor/nekojs/client/render/WorldRenderContextJS.java) resolved `Minecraft.renderBuffers().bufferSource()` reflectively. Minecraft 26.2 removes that immediate buffer source. The lookup returned no source, and both public helpers returned without submitting geometry. Registration and callbacks still ran; changing registration order could not repair the missing draw.

The fix belongs at the version rendering boundary, not in registration, Probe, post effects, or wiki content. Minecraft 1.21.1 retains its existing context implementation; a small [1.21.1 facade override](../../versions/1.21.1/src/main/java/com/tkisor/nekojs/client/render/WorldRenderPlatform.java) preserves compilation against its `RenderType.lines()` API rather than selecting the shared 26.1 imports. The shared [26.1 facade](../../src/main/java/com/tkisor/nekojs/client/render/WorldRenderPlatform.java) keeps the immediate API. The [26.2 NeoForge facade](../../versions/26.2.0/src/main/java/com/tkisor/nekojs/client/render/WorldRenderPlatform.java) and [26.2 Fabric facade](../../versions/26.2.0-fabric/src/main/java/com/tkisor/nekojs/client/render/WorldRenderPlatform.java) use staged submission. Public script signatures and registration behavior do not change.

A second source-backed defect appeared during review: NeoForge calls `AfterLevel` from `GameRenderer.renderLevel` after `LevelRenderer.render` has popped its camera model-view transform. Preparing against ambient state loses camera rotation at the late layer, even when vertices are camera-relative. Both 26.2 facades now push the model-view stack, set the current camera rotation, and restore the previous stack in `finally`. Setting rather than multiplying also avoids applying rotation twice in earlier layers.

## Regression design

[WorldRenderPipelineTest](../../src/test/java/com/tkisor/nekojs/client/render/WorldRenderPipelineTest.java) compiles and executes the production public context, actual `UiColor`, and each 26.2 node facade in isolated classloaders against recording game contracts. The fixture deliberately exposes a 26.2 `RenderBuffers` without `bufferSource()`: the original helper compiles and executes but emits zero vertices, so the regression fails an observable rendering assertion rather than a source-text check or missing-symbol compilation.

The fixture also starts with a non-camera ambient model-view, like the late callback. `RenderType.prepare()` requires the camera transform. Without the scoped correction the actual production facade throws `missing late-stage camera rotation`; with it, drawing succeeds and the incoming stack is restored.

The serialized focused command used by the Team Lead is:

```shell
./gradlew :26.2.0:test --tests com.tkisor.nekojs.client.render.WorldRenderPipelineTest -x :common:compileJava -x :26.2.0:compileJava --max-workers=1
```

The two compile exclusions isolate the recording fixture from concurrent unrelated compilation changes. The test still compiles the actual production helper and both facades through `JavaCompiler`. This command does **not** replace actual-node compilation.

The five tests cover fluent returns; camera-relative coordinates before float narrowing at large world positions; unsigned ARGB bits; normalized directions; minimum per-vertex width; all twelve distinct box edges in one submission; append/upload/prepare/draw/close order; scoped camera binding and restoration; stage cleanup and recovery after emit/upload/prepare/execute-info/draw failures; and invalid colors before GPU allocation. These are CPU orchestration contracts, not visual or native-driver acceptance.

## Pipeline and resource ownership

The cached Minecraft 26.2 patched source establishes the replacement flow:

1. Select `RenderTypes.linesTranslucent()` and its vertex format/topology.
2. Append one draw to a dedicated `StagedVertexBuffer` and emit through its vertex builder.
3. Upload the complete draw.
4. Scope the current camera model-view, prepare the render type, and obtain nonempty execute information.
5. Call `PreparedRenderType.drawFromBuffer`, which binds the output color/depth target, default uniforms, dynamic transforms, vertex/index buffers, and an indexed draw inside a scoped render pass.
6. Restore the incoming model-view and close the owned stage after synchronous submission returns, including exceptional paths.

The helper must not upload, end, or close the vanilla shared stage: a callback may run while the prepared vanilla feature frame still owns it. No static Minecraft instance, buffer source, guest value, or GPU resource is retained. A box is one stage and one draw, not twelve per-edge allocations.

Source evidence in the cached patched archive: `StagedVertexBuffer` append/upload/execute-info/close contracts; `RenderTypeFeatureRenderer` vanilla orchestration; `PreparedRenderType` scoped pass submission; and `BufferBuilder` endpoint duplication for line topology. `LevelRenderer` clears targets and pops model-view at lines 254–255; `GameRenderer` posts `AfterLevel` at line 572. The weather pass precedes the transparency composite, while the cleared late item target falls back to the main target through `OutputTarget.getRenderTarget()`. Vulkan buffer close queues deferred destruction; GL submits the draw before deleting the buffer object. A disposable stage does not require reusing or ending the vanilla frame.

An independent read-only review confirmed staged orchestration, line endpoint/index semantics, ownership, geometry, and close timing against the actual source. Its scope excluded callback sites; the late-transform finding above comes from the additional callback/renderer integration audit.

## Verification status

- Original red: Lead job `bash-80bb31fac8634323`, exit 1, five tests/five failures; the original public helper compiled and executed but submitted no vertices.
- Initial staged green: Lead job `bash-b56aa75806514d93`, exit 0, five tests passed. Superseded as final acceptance by the subsequent camera regression.
- Camera red: Lead job `bash-0f4b3e2887494f08`, exit 1, five tests/four failures; exact failure `java.lang.IllegalStateException: missing late-stage camera rotation` at fixture `RenderType.prepare` and actual `WorldRenderPlatform.draw`.
- A final-green attempt, Lead job `bash-2243bb236aa444d0`, stopped during test compilation before this fixture ran: another task had added a test for an uncompiled production class while production compilation was excluded. This is integration ordering, not a new behavior result; the previous camera-red XML is not reused as a new run.
- Final scoped green: Lead job `bash-76715383f31b4b73` compiled the actual 26.2 node and ran `WorldRenderPipelineTest`: five tests, zero failures, zero skipped. The whole job exited 1 because two independent new-builder tests failed parsing ES5 arrow syntax; it is **not** reported as a whole-job pass.
- Remaining node and Fabric compilation: pending coordinated Lead runs.
- Scoped diff check passed; seven-file English/trailing-whitespace audit found zero issues; both 26.2 facades are identical. Final source review confirmed unchanged public signatures and no registration/platform-gate edits.
- No Gradle or Stonecutter command is run by this teammate.
- No live Minecraft visual, multiplayer, or maintainer acceptance has been performed.

## Remaining limitations

The fixture does not bootstrap Minecraft, execute a GPU command encoder, exercise real vertex/index builders, verify numerical matrix math or target selection, or prove depth/alpha appearance. Actual node compilation is a separate required check. Fabric retains its existing registration-surface restrictions; a compatible helper does not add a Fabric world-render event. Only 26.2 receives explicit phase-independent camera binding here; older paths retain their preexisting transform behavior and are not certified by this fixture.

Late-layer depth testing uses the current vanilla depth target, not a saved pre-pass depth image. `LevelRenderer.addAlwaysOnTopPass` conditionally clears main depth before `AfterLevel`; when that pass ran, late geometry cannot promise original-world occlusion. The normal `AfterWeather` callback precedes that clear. The world projection remains installed through `AfterLevel`, before the hand projection replaces it. This fix does not reorder events or capture/restore vanilla depth.

A dedicated short-lived stage prioritizes explicit ownership over a static cache. Vanilla GPU pools round allocations to 262,144 bytes, so frequent individual helper calls incur allocation/upload overhead despite deterministic release. A box batches twelve edges into one call. A reusable per-frame pool needs a separately owned lifecycle hook and is not introduced by this fix.
