# LostLoot

Minecraft mod that saves despawned items in a server pool so players can **fish them up** or **brush them out** of suspicious sand/gravel.  
With admin tools and optional filters and limits.

[![Modrinth](https://img.shields.io/modrinth/dt/jahus-lost-loot-upgraded?logo=modrinth&label=Modrinth)](https://modrinth.com/mod/jahus-lost-loot-upgraded)
[![CurseForge](https://img.shields.io/curseforge/dt/1803244?logo=curseforge&label=CurseForge)](https://www.curseforge.com/projects/1803244)

Server-side / singleplayer. Clients do not need the mod.  
Dependencies: Fabric API.

Based on [PandaArchaeology](https://github.com/TreeOfSelf/PandaArchaeology) by [TreeOfSelf](https://github.com/TreeOfSelf) (CC0).

## Mechanics

- When an item entity **despawns**, it may be stored in a persistent pool (`LostLoot.dat` in the world folder).
- **Fishing** (open water) and **archaeology** (suspicious sand/gravel) each have a configurable chance to pull an entry from that pool instead of (or as) the normal loot.
- A message accompanies a find event. It shows the item (hover for enchants / components), who lost it (if known), and the date it was lost.

## Config

File: `config/lost-loot.json` (created/updated on first launch).

```json
{
  "activeForFishing": true,
  "activeForBrushing": true,
  "onlyPlayerOwned": true,
  "itemLimit": 10,
  "globalItemLimit": 256,
  "fishingChance": 100,
  "brushChance": 10,
  "luckMultiplier": 3,
  "global_broadcast": false,
  "rarity": [],
  "item_filter_mode": "exclude",
  "item_filter": [
    "#minecraft:flowers",
    "minecraft:dirt",
    "minecraft:cobblestone",
    "minecraft:kelp",
    "minecraft:egg",
    "minecraft:sand",
    "minecraft:gravel",
    { "id": "minecraft:iron_axe", "rarity": ["common"] }
  ]
}
```

| Key                 | Default     | Meaning                                                                                                  |
|---------------------|-------------|----------------------------------------------------------------------------------------------------------|
| `activeForFishing`  | `true`      | Allow finds while fishing (open water only)                                                              |
| `activeForBrushing` | `true`      | Allow finds when brushing suspicious blocks                                                              |
| `onlyPlayerOwned`   | `true`      | Only store items that **had a player owner** (drops / death)                                             |
| `itemLimit`         | `10`        | Max entries **per item type** (`0` = unlimited). FIFO: oldest of that type is removed when full          |
| `globalItemLimit`   | `256`       | Max entries **in the whole pool** (`0` = unlimited). FIFO: oldest overall is removed when full           |
| `fishingChance`     | `100`       | `1 in N` base chance on a fishing catch (luck improves the roll)                                         |
| `brushChance`       | `10`        | `1 in N` base chance after archaeology loot is rolled                                                    |
| `luckMultiplier`    | `3`         | How strongly Luck of the Sea and the Luck effect improve the roll                                        |
| `global_broadcast`  | `false`     | `false`: message to finder only; `true`: whole server                                                    |
| `rarity`            | `[]`        | Global allow-list of item rarities (`common` / `uncommon` / `rare` / `epic`). Empty = no filter          |
| `item_filter_mode`  | `exclude`   | `none`, `include`, `exclude` (see below)                                                                 |
| `item_filter`       | (see above) | Strings (item id: `minecraft:dirt`, item tag: `#minecraft:flowers`)<br/>Objects `{ "id", "rarity": [] }` |

### Item filter, rarity and global rarity

Two layers, in this order:

1. **`rarity`** (optional global filter)
2. **`item_filter_mode` + `item_filter`** (include / exclude / none)

#### Global `rarity`

```json
"rarity": ["rare", "epic"]
```

Only rare and epic stacks may enter the pool.  
`[]` or omit: no global rarity gate.

#### Filter modes

- **`none`**: no id/tag filter (only `onlyPlayerOwned` applies)
- **`exclude`**: store everything allowed by ownership **except** listed items
- **`include`**: **only** listed items are added to the pool (still subject to `onlyPlayerOwned`)

Filter is always applied **in addition** to `onlyPlayerOwned`.

#### `item_filter` entries

| Form                                                 | Meaning                                                          |
|------------------------------------------------------|------------------------------------------------------------------|
| `"minecraft:dirt"`                                   | Match that item, **any** rarity                                  |
| `"#minecraft:flowers"`                               | Match that tag, **any** rarity                                   |
| `{ "id": "minecraft:iron_axe", "rarity": ["epic"] }` | Match only if id/tag matches **and** stack rarity is in the list |

The same object form works in **include** and **exclude**: the `rarity` array restricts *when the rule matches*, it is not inverted by mode.

Examples:

- **Exclude** all flowers, and exclude only **common** iron axes:
  ```json
  "item_filter_mode": "exclude",
  "item_filter": [
    "#minecraft:flowers",
    { "id": "minecraft:iron_axe", "rarity": ["common"] }
  ]
  ```
- **Include** only epic iron axes and any diamond:
  ```json
  "item_filter_mode": "include",
  "item_filter": [
    "minecraft:diamond",
    { "id": "minecraft:iron_axe", "rarity": ["epic"] }
  ]
  ```

Old configs with `"min_rarity"` are migrated.  
Example: `"rare"` is migrated to `"rarity": ["rare", "epic"]` (that floor and above).

### Chances

Rolls use `nextInt(N)` (values `0 … N-1`). A find succeeds when:

```
nextInt(N) ≤ effectiveLuck × luckMultiplier
```

- **Fishing:** `N = fishingChance`, `effectiveLuck = Luck of the Sea level + player Luck attribute`
- **Brushing:** `N = brushChance`, `effectiveLuck = player Luck attribute` only (Luck of the Sea does not apply)

With **defaults** (`fishingChance = 100`, `brushChance = 10`, `luckMultiplier = 3`):

**Fishing**

| Setup                        | effectiveLuck | Success chance     |
|------------------------------|---------------|--------------------|
| No luck                      | 0             | 1 / 100 (**1%**)   |
| Luck of the Sea I            | 1             | 4 / 100 (**4%**)   |
| Luck of the Sea II           | 2             | 7 / 100 (**7%**)   |
| Luck of the Sea III          | 3             | 10 / 100 (**10%**) |
| Luck of the Sea III + Luck I | 4             | 13 / 100 (**13%**) |

**Brushing**

| Setup    | effectiveLuck | Success chance     |
|----------|---------------|--------------------|
| No luck  | 0             | 1 / 10 (**10%**)   |
| Luck I   | 1             | 4 / 10 (**40%**)   |
| Luck II  | 2             | 7 / 10 (**70%**)   |
| Luck III | 3             | 10 / 10 (**100%**) |

If `effectiveLuck × luckMultiplier ≥ N`, the find is guaranteed (every successful open-water catch / brush roll).

### Advice

- Prefer `"onlyPlayerOwned": true`.
- If using a filter, prefer `"item_filter_mode": "include"` and use [item tags](https://minecraft.wiki/w/Item_tag_(Java_Edition)) as much as possible.
- Consider the social effects of `"global_broadcast": true` before enabling it.
- Default brush rates climb quickly with Luck potions; raise `brushChance` or lower `luckMultiplier` if that feels too high.
- Leave `"rarity": []` unless you want a global quality gate; use per-entry `"rarity"` for exceptions (example: only rare axes).

## Admin commands

Permission: **game masters** (level 2).

| Command                  | Description                                                                                                                            |
|--------------------------|----------------------------------------------------------------------------------------------------------------------------------------|
| `/lostloot list`         | List the pool with **1-based** identifiers, hoverable item names, owner and date                                                       |
| `/lostloot delete <id>`  | Delete one entry from the pool                                                                                                         |
| `/lostloot clear`        | **Empty the pool**                                                                                                                     |
| `/lostloot recover <id>` | Give that entry to the executing **player** inventory (does **not** drop; fails if inventory is full so nothing is lost from the pool) |

Identifiers match `/lostloot list` (starting at `1`).

## Persistence
- Pool file: `<world>/LostLoot.dat` (NBT, atomic write via temp file).
- Saved on vanilla autosave, clean shutdown, and after pool mutations (find, remove, clear, recover).
- A hard crash between autosaves can lose **recently despawned** entries not yet flushed; finds that pull from the pool are saved promptly to avoid double-hold.

## Credits
- Original idea and base implementation: [@TreeOfSelf](https://github.com/TreeOfSelf) ([PandaArchaeology](https://github.com/TreeOfSelf/PandaArchaeology), CC0)
- LostLoot fork and features: [@DarthJahus](https://github.com/DarthJahus)

## License
Apache-2.0
