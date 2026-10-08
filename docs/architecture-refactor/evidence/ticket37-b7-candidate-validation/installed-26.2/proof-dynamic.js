DynamicRegistryEvents.dynamicRegistry(event => {
    const item = event.item('ticket37_b7:proof_item', build => {
        build.maxStackSize = 16;
        build.rarity = 'epic';
    });
    const sound = event.soundEvent('ticket37_b7:proof_sound', build => {
        build.fixedRange = null;
    });
    const effect = event.mobEffect('ticket37_b7:proof_effect', build => {
        build.category = 'beneficial';
        build.color = 0x123456;
    });
    console.info('B7-INSTALLED-DYNAMIC-COLLECT item=' + item + ' sound=' + sound + ' effect=' + effect);
});
