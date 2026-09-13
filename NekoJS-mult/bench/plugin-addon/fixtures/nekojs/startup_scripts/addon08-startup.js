// Ticket 08 fixture: consume the external addon's frozen binding product from STARTUP.
// The binding is contributed by bench/plugin-addon/fixture (a real external addon jar dropped
// into run/mods) through the pre-existing nekojs:bindings extension point.
console.info('NEKOJS-ADDON08 startup-binding ' + AddonProbe.hit('startup'));

// AC6 controls: two sibling classes in the same external addon jar are discovered by the same real
// loader path but must be filtered out (requiredMods names a mod that is never present; the other is
// clientOnly on a dedicated server). Their bindings must therefore be absent from this environment.
console.info('NEKOJS-ADDON08 startup-filtered-requiredmods-absent ' + (typeof AddonFilteredLeak === 'undefined'));
console.info('NEKOJS-ADDON08 startup-filtered-clientonly-absent ' + (typeof AddonClientOnlyLeak === 'undefined'));
