declare const legacy: SoundEventBuilder;
legacy.fixedRange = null;

DynamicRegistryEvents.dynamicRegistry(event => {
    const created: boolean = event.item('test:typed', build => {
        build.maxStackSize = 16;
        // @ts-expect-error Unassociated same-name metadata is not part of the event builder.
        build.spoofMember = true;
    });
    const sound: boolean = event.soundEvent('test:sound', build => {
        const fluent: DynamicSoundEventBuilder = build.setFixedRange(null).setMode('world');
        fluent.fixedRange = null;
    });
    event.mobEffect('test:effect', build => { build.setColor(42).setMode('world'); });
    // @ts-expect-error Dynamic payload has no block operation.
    event.block('test:invalid');
    // @ts-expect-error Explicit null is not an optional executable callback.
    event.item('test:invalid', null);
    void created;
    void sound;
});
