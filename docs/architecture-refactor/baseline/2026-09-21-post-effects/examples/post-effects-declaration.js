// Ticket 28 minimal runnable example: post-effect declaration vs runtime binding.
//
// Declaration lifecycle (candidate -> commit, generation-scoped):
//   ClientEvents.postEffects collects register/unregister declarations into an inert
//   candidate plan. Nothing is installed until the client reload commit point, so an
//   invalid batch or an interrupted reload keeps the previous active generation.
//
// Runtime binding (never replaced by the declaration event):
//   PostEffects.set / clear / toggle / current / isActive act on the live client frame.
//
// Read-only declaration query:
//   PostEffects.hasDefinition / installed / activeGeneration report which generation
//   owns a definition, so an id captured from an older generation fails loudly instead
//   of silently reading stale state.

ClientEvents.postEffects(event => {
  // Runtime chain declared through the existing ClientEvents resource/reload sub-event.
  event.register('nekojs:gray', {
    fragmentShader: [
      '#version 150',
      'uniform sampler2D InSampler;',
      'in vec2 texCoord;',
      'out vec4 fragColor;',
      'void main() {',
      '  vec4 c = texture(InSampler, texCoord);',
      '  float g = dot(c.rgb, vec3(0.299, 0.587, 0.114));',
      '  fragColor = vec4(g, g, g, c.a);',
      '}'
    ].join('\n'),
    uniformsJson: '{"Intensity": 1.0}'
  })

  // Retire an effect a previous generation declared (or an earlier declaration here).
  event.unregister('nekojs:old_effect')
})

ClientEvents.tickPost(() => {
  // Runtime actions stay on the PostEffects binding; only resource-backed ids activate.
  if (PostEffects.isAvailable('minecraft:invert')) {
    PostEffects.set('minecraft:invert')
  }
})

ClientEvents.loggedOut(() => {
  PostEffects.clear()
})

// Generation/stale query: after a successful reload this is the new generation number,
// and a retired id reports false instead of pretending to still exist.
global.reportPostEffects = () => {
  console.log('active post-effect generation =', PostEffects.activeGeneration())
  console.log('declared ids =', PostEffects.installed().join(', '))
  console.log('nekojs:gray declared =', PostEffects.hasDefinition('nekojs:gray'))
}
