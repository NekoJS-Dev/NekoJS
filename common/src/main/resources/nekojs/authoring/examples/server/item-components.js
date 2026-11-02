// Detached stacks only: component decoding still needs the running instance registry and codec.
const exampleSword = Item.of("minecraft:iron_sword[damage=2]", 1);
const exampleStones = Item.of("minecraft:stone", 4);
console.info("Detached sword with damage 2:");
console.info(exampleSword);
console.info("Detached stone stack with count 4:");
console.info(exampleStones);
