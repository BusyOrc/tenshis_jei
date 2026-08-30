# Tenshi's JEI Addon

A NeoForge 1.21.1 addon mod for [JEIU](https://github.com/StardustMINUS-01/JustEnoughItems) (unofficial JEI fork) that makes JEIU recipe-tree shift+C auto-crafting work in the Extended Terminal.

## Recipe-tree auto-crafting modes

Config file: `config/tenshis_jei_addon.toml` — key `recipeTreeCraftingMode`:

- `EXACT` (default, new logic): always craft exactly the quantity set in the recipe tree, regardless of how many result items are already in the inventory (the backpack ends with n + y).
- `LEGACY` (old logic): keep JEIU's built-in inventory-aware top-up behavior.

`debug = false` (default) writes no log output at all.

## Dependencies

- NeoForge 21.1.x (tested with 21.1.248)
- JEIU (mod id `jei`)
- Extended Terminal (mod id `extendedterminal`)

## License

MIT