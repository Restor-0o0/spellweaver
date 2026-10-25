<p align="center"><img src="src/main/resources/spellweaver.png" alt="Spellweaver" width="160"></p>

# Spellweaver (Magicka-like spellcasting)

Magicka-style elemental spellcasting for **Minecraft 26.1.2 · NeoForge**. No wands, no mana bars
to grind: press up to five elements, then cast. Every combination does something.

*Русское руководство: [docs/GUIDE_RU.md](docs/GUIDE_RU.md)*

> **You shape this mod.** I read the comments almost every day. Ideas, bug reports and balance
> complaints from players have already turned into features. If something is missing, too strong
> or too weak, leave a comment and it may well be in the next release.

## Features

- **8 elements, 1286 spells.** Fire, Water, Cold, Lightning, Earth, Life, Arcane and Death combine
  into every possible formula of one to five elements, each with its own effect.
- **204 signature spells** in the Grimoire, searchable by name or tag (`#building`, `#damage`,
  `#summon`, `#support`…), with four quick-cast slots.
- **Tiers that matter.** One to three elements are support, hexes and jokes; four is the combat tier;
  five is the top shelf with ritual-ordered ultimates — storms, meteor showers, tornadoes and
  elemental titans rising from the ground.
- **Shape the world.** Bridges, stairs, walls, pillars, ice spikes and a 5×5×5 shape editor for your
  own stone sculptures. Earth and roots stay; ice and arcane fade.
- **Status reactions.** Wet amplifies cold and lightning, fire thaws ice, life heals, death drains
  and raises undead servants.
- **18 specialist enemies** — shield beetles, goblin mages, countermages and more — each built to
  test a different school of magic.
- **Gear, charms and potions** that tune your element powers, area, duration, summons and cast speed.
- **Datapack spells.** Any recipe can be added or replaced — see [RECIPE_FORMAT.md](docs/RECIPE_FORMAT.md).

## The mage's path

Spellweaver is meant to be played as a journey: you survive, travel and grow as a mage.

- **Mage level.** Casting, killing hostile mobs with magic and reading new scrolls give mage
  experience. A new mage weaves formulas of three elements; four elements open at level 3 and five
  at level 8. Every level adds 1% cast speed and 1% spell damage. Your level, experience and scroll
  count are shown at the top of the Grimoire.
- **Spell scrolls.** The strongest spells (elementals, ultimates, legendary spells) are sealed until
  you read their scroll. Scrolls are never used up.
- **Scrolls come from the world.** Any hostile mob from any mod has a small chance to drop one.
  Witches, evokers and other spellcasters drop them more often. Specialist enemies drop them more
  often still, and bosses always drop one, with ultimates the most likely. Drops prefer scrolls you
  have not read yet.
- **Where you travel matters.** Scrolls are found in chests of every structure, including those
  from dungeon mods. Nether scrolls lean towards fire, End scrolls towards arcane, and both carry
  more ultimates.
- **Mobs have elemental natures.** Fire-immune mobs shrug off fire and fear water and cold; undead
  resist death and burn under life magic; aquatic mobs resist water and conduct lightning; End
  creatures resist arcane. This works for mobs from other mods automatically, and packs can tune it
  with entity tags.
- **Advancements.** A "Mage Path" tab follows you from your first cast and first scroll to the
  complete grimoire, a boss slain by magic and the archmage rank.

## Mod compatibility

Spellweaver needs no other mods. It works with mobs, bosses and structures from any mod, and has
tuned data for these dungeon mods:

- **L_Ender's Cataclysm**: Ignis, the Netherite Monstrosity, the Leviathan, the Harbinger, Scylla,
  Maledictus and the other bosses count as bosses and have their own elemental resistances.
- **Mowzie's Mobs**: Frostmaw, the Ferrous Wroughtnaut, Umvuthi and the Sculptor.
- **Aquamirae**: Captain Cornelia.
- **When Dungeons Arise**, **YUNG's Better Dungeons** and any other structure mod: their chests
  can hold spell scrolls.

Modpack makers can extend this with entity type tags:

| Tag | Effect |
| --- | --- |
| `magicka:bosses` | Counts as a boss (also `c:bosses`) |
| `magicka:spellcasters` | Uses the caster scroll chance |
| `magicka:no_scroll_drops` | Never drops scrolls |
| `magicka:resists/<element>` | Takes less damage from that element |
| `magicka:weak/<element>` | Takes more damage from that element |

## Controls

| Key | Action |
| --- | --- |
| G | Toggle magic mode |
| 1 / 2 / 3 / 4 | Fire / Water / Cold / Lightning |
| Q / E / R / F | Earth / Life / Arcane / Death |
| Left click | Cast at your aim |
| Right click | Cast on yourself |
| V | Clear the formula |
| B | Open the Grimoire |
| Alt + 1–4 | Fill a formula from a quick slot |
| H | Toggle instant cast |
| K | Shape editor |
| J | Loadout (gear and potion belt) |
| Z / X | Drink a belt potion |

Element keys only take over while magic mode is on. All keys can be rebound.

## Configuration

Every feature can be tuned or switched off. Don't like mage levels, scrolls from every mob or
spells that break blocks? Turn them off. There are three ways.

**In game.** Open the Grimoire (B) and press ⚙, or go to *Mods → Spellweaver → Config*. Each
option has a description. In multiplayer only the server can change settings.

**In the file.** Settings live in `config/spellweaver-server.toml`, created on the first launch.
Every option in it has a comment. Change `true` to `false` or edit a number, then restart the game
or the server. The file is synced to clients, so players don't need to change anything.

**Quick switches.** Copy this block into a new `spellweaver-server.toml` and remove `#` from the
lines you want. Put it in `config/` (replace the old file) or in `defaultconfigs/` for a modpack.
Options you leave out keep their defaults.

```toml
# Classic mode: no mage levels, five elements from the start
#mageLevels = false
#formulaLengthByLevel = false

# All spells open, no scrolls needed
#unlockAllSpells = true

# Ordinary mobs drop no scrolls, only specialists and bosses
#scrollDropsFromAllMobs = false

# No scrolls in chests
#chestScrollMultiplier = 0.0

# Same scrolls in every dimension
#dimensionScrollBias = false

# Mobs take normal damage from every element
#mobElementResistances = false

# Keep the world intact: no broken blocks, no placed blocks
#allowTerrainDamage = false
#constructiveMagic = false

# Peaceful multiplayer
#friendlyFire = false
#playerMagicDamageMultiplier = 0.0

# No Spellweaver enemies spawning in the world
#specialEnemyNaturalSpawns = false
```

Don't paste the block into a file that already has these options: a duplicate key breaks the file
and NeoForge resets it.

**Progression**

| Option | Default | Meaning |
| --- | --- | --- |
| `unlockAllSpells` | false | Open every sealed spell without scrolls |
| `mageLevels` | true | Mage level and mage experience |
| `mageXpMultiplier` | 1.0 | Scales all mage experience |
| `maxMageLevel` | 30 | Level cap |
| `formulaLengthByLevel` | true | Limit formula length by mage level (never in creative) |
| `fourElementsLevel` | 3 | Level that opens four-element formulas |
| `fiveElementsLevel` | 8 | Level that opens five-element formulas |
| `castSpeedPerLevel` | 1 | Cast speed per level, % |
| `damagePerLevel` | 1 | Spell damage per level, % |

**Scrolls**

| Option | Default | Meaning |
| --- | --- | --- |
| `scrollDropsFromAllMobs` | true | Any hostile mob can drop a scroll |
| `mobScrollChance` | 0.005 | Ordinary hostile mob |
| `casterMobScrollChance` | 0.05 | Witches, evokers, `magicka:spellcasters` |
| `specialistScrollChance` | 0.04 | Spellweaver specialist enemies |
| `specialistCasterScrollChance` | 0.1 | Specialist casters |
| `bossScrollChance` | 1.0 | Bosses |
| `bossHealthThreshold` | 150 | Hostile mobs with this much max health count as bosses; 0 disables |
| `bossUltimateWeight` | 3 | How much likelier an ultimate is in a boss drop |
| `dimensionScrollBias` | true | Nether leans to fire, End to arcane |
| `chestScrollMultiplier` | 1.0 | Scales the chance of a scroll in chests; 0 disables |

**Elemental resistances**

| Option | Default | Meaning |
| --- | --- | --- |
| `mobElementResistances` | true | Mobs resist or suffer elements by their nature |
| `elementResistMultiplier` | 0.5 | Damage taken from a resisted element |
| `elementWeaknessMultiplier` | 1.5 | Damage taken from a weakness |

**World and combat**

| Option | Default | Meaning |
| --- | --- | --- |
| `constructiveMagic` | true | Spells may place blocks |
| `allowTerrainDamage` | true | Spells may break blocks |
| `magicBlockLifetime` | true | Magic blocks fade over time |
| `permanentEarthBlocks` | true | Earth constructions stay |
| `permanentRootBlocks` | true | Roots stay |
| `earthLifetimeTicks` / `iceLifetimeTicks` / `rootLifetimeTicks` / `arcaneLifetimeTicks` | 1200 / 600 / 900 / 400 | Lifetimes of fading blocks |
| `maxConstructionBlocks` | 16 | Block budget of one construction |
| `collapseWithRoot` | true | Constructions collapse when their base is gone |
| `castTimeMultiplier` | 1.0 | Scales every cast time |
| `channelMaxTicksPerElement` | 20 | Channel length per element |
| `playerMagicDamageMultiplier` | 0.5 | Spell damage dealt to players |
| `friendlyFire` | true | Spells can hurt other players |
| `maxActiveSpellsPerPlayer` | 32 | Running spells per player |
| `revivalWindowTicks` | 600 | How long a fallen creature can still be revived |
| `maxUndeadMinionsPerPlayer` | 24 | Undead servants per player |
| `specialEnemyNaturalSpawns` | true | Specialist enemies spawn in the world |

## Installation

Requires **NeoForge 26.1.2.71+** for Minecraft **26.1** and Java 25. Install the same version on
the client and the server. No other dependencies.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Gametests: `./gradlew runGameTestServer`.

## License

[MIT](LICENSE). Spellweaver is an independent fan project and is not affiliated with Paradox
Interactive or the Magicka series.
