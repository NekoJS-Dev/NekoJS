// Installed-artifact fixture. The runner substitutes the phase, declaration and node reader.
/* DECLARATION */
let ticks = 0;
ServerEvents.tickPost(() => {
    if (++ticks % 20 !== 0) return;
    const query = VillagerTrades.query();
    /* POOL_READER */
    console.info('DOMAIN-CLOSEOUT-TRADE ' + JSON.stringify({
        phase: 'PHASE', status: String(query.status), generation: query.generation,
        total: query.total, unrestored: query.unrestoredListingKeys.size(),
        retired: query.retiredListingKeys.size(), physicalCount
    }));
});
