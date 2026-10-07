# Observed native quote sample

Date: 2026-10-07 (local UTC+8). Source: isolated official-artifact NeoForge 26.2.0 server, public vanilla `data get entity @e[tag=ticket37-trading,limit=1] Offers` after native right-click attempts. The ordinary quote generation occurred on a fresh farmer with normal AI, `Xp:1`, profession `minecraft:farmer`, level 1; movement speed was set to zero with the vanilla attribute command. User worlds were not used.

Declared input:

```js
ServerEvents.tradeDeclaration(event => {
  event.add('minecraft:farmer/level_1', {
    cost: '1x minecraft:emerald', result: '5x minecraft:apple',
    maxUses: 12, xp: 2, priceMultiplier: 0.05
  });
});
```

The bounded sample allowed at most 12 fresh tagged farmers. Recorded outputs:

```text
attempt=1 Farmer has the following entity data: {Recipes: [{maxUses: 16, buy: {count: 15, id: "minecraft:beetroot"}, sell: {count: 1, id: "minecraft:emerald"}, xp: 2, priceMultiplier: 0.05f}, {maxUses: 16, buy: {count: 20, id: "minecraft:wheat"}, sell: {count: 1, id: "minecraft:emerald"}, xp: 2, priceMultiplier: 0.05f}]}
attempt=2 Farmer has the following entity data: {Recipes: [{maxUses: 16, buy: {count: 15, id: "minecraft:beetroot"}, sell: {count: 1, id: "minecraft:emerald"}, xp: 2, priceMultiplier: 0.05f}, {maxUses: 16, buy: {count: 22, id: "minecraft:carrot"}, sell: {count: 1, id: "minecraft:emerald"}, xp: 2, priceMultiplier: 0.05f}]}
attempt=3 Found no elements matching Offers
attempt=4 Farmer has the following entity data: {Recipes: [{maxUses: 16, buy: {count: 15, id: "minecraft:beetroot"}, sell: {count: 1, id: "minecraft:emerald"}, xp: 2, priceMultiplier: 0.05f}, {maxUses: 12, buy: {count: 1, id: "minecraft:emerald"}, sell: {count: 5, id: "minecraft:apple"}, xp: 2, priceMultiplier: 0.05f}]}
ACTUAL-CUSTOM-OFFER-OBSERVED
```

The matching saved offer proves real villager offer generation includes the script declaration. It does not prove a completed exchange, restock, retirement/replacement, other nodes, or a visible merchant UI. The PNG captured in this iteration shows the farmer/world and console chat, not the offer panel; the offer assertion is the public vanilla data above. Earlier NoAI/profession-reset fixture attempts are described in VERIFICATION.md and were not discarded as passing samples.
