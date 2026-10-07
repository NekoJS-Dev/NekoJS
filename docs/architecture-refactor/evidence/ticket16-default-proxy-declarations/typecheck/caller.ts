DynamicRegistryEvents.dynamicRegistry(event => {
    const itemDefault: boolean = event.item('test:item_default');
    const soundDefault: boolean = event.soundEvent('test:sound_default');
    const effectDefault: boolean = event.mobEffect('test:effect_default');
    const itemTyped: boolean = event.item('test:item', build => {
        build.maxStackSize = 16;
        const chained: DynamicItemBuilder = build.setMode('world');
        // @ts-expect-error Sound-only range is not on the concrete item builder.
        build.fixedRange = null;
    });
    const soundTyped: boolean = event.soundEvent('test:sound', build => {
        build.fixedRange = null;
        const chained: DynamicSoundEventBuilder = build.setFixedRange(null).setMode('world');
    });
    const effectTyped: boolean = event.mobEffect('test:effect', build => {
        build.color = 42;
        const chained: DynamicMobEffectBuilder = build.setMode('world');
    });
    // @ts-expect-error The closed payload has no block entry.
    event.block('test:forbidden');
    // @ts-expect-error Explicit null is not callback omission.
    event.item('test:null_callback', null);
});
