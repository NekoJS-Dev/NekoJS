// Ticket 26 real-machine smoke: keybind registration, input events, consumeClick polling,
// HUD listener and resident HUD renderer. Markers carry the T26- prefix for log collection.
const dashKey = KeyBindEvents.register('t26:dash', 'key.keyboard.r', 'movement')
console.info('T26-REGISTERED id=t26:dash key=R handle=' + (dashKey !== null && dashKey !== undefined))

ClientEvents.tickPost(() => {
  if (dashKey.consumeClick()) {
    console.info('T26-CONSUMECLICK poll consumed a click')
  }
})

KeyBindEvents.pressed('t26:dash', event => {
  console.info('T26-PRESSED id=' + event.id + ' down=' + event.down)
})

KeyBindEvents.released('t26:dash', event => {
  console.info('T26-RELEASED id=' + event.id)
})

KeyBindEvents.tick('t26:dash', event => {
  console.info('T26-HELD id=' + event.id)
})

ClientEvents.hud(painter => {
  painter.text('T26 HUD listener live', 4, 4, 0xFFFFFF00)
})

ClientEvents.hudRender('t26:badge', { layer: 'foreground', priority: 100 }, (ctx) => {
  ctx.rect(4, 18, 140, 12, 0x40000000)
  ctx.text('T26 BADGE (hudRender)', 6, 20, 0xFF55FF55)
})
