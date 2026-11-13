<p align="center"><img src="src/main/resources/spellweaver.png" alt="Spellweaver" width="160"></p>

# Spellweaver (Magicka-like spellcasting)

Magicka-style elemental spellcasting for **Minecraft 1.21.1 · NeoForge**. Press up to five
elements, then cast. Every combination does something. The default balance uses recovery and
shared cooldowns; optional element cooldowns, mana and strain can replace or complement it.

*Русское руководство: [docs/GUIDE_RU.md](docs/GUIDE_RU.md)*

Languages: English, Russian, German, Spanish, French, Brazilian Portuguese, Simplified
Chinese and Japanese. Additional languages include the complete spell catalogue; they use
machine translations with reviewed interface terminology. See [localization](docs/LOCALIZATION.md).

> **You shape this mod.** I read the comments almost every day. Ideas, bug reports and balance
> complaints from players have already turned into features. If something is missing, too strong
> or too weak, leave a comment and it may well be in the next release.

## Contents

[Features](#features) · [Progression](#the-mages-path) · [Compatibility](#mod-compatibility) ·
[Controls](#controls) · [Configuration](#configuration) · [Casting balance](#casting-balance) ·
[Installation](#installation) · [Building](#building) · [License](#license)

Balance: [Modes](#balance-modes) · [Groups and cooldowns](#spell-groups-and-cooldowns) ·
[Mana and strain](#mana-and-strain) · [Active limits](#active-cast-limits) ·
[Per-spell settings](#per-spell-settings) · [Server commands](#server-balance-commands)

## Features

- **8 elements, 1286 formulas.** Fire, Water, Cold, Lightning, Earth, Life, Arcane and Death combine
  into every formula of one to five elements: 346 hand-made recipes, and every other set fuses by
  clear rules — the lead element sets the form, each element adds its mechanic, pairs react.
- **A living Grimoire.** A leather-bound book whose pages really turn. Its first chapters teach
  casting and the fusion rules, then come **206 signature spells**: search them by name, page
  number or tag (`#building`, `#damage`, `#summon`, `#support`…), filter by element, tag or formula
  length, and drag any spell into one of four quick-cast slots.
- **Rune circles.** Every spell has its own rune written from 24 element glyphs. It flashes at your
  hand, marks the zone of area spells, and meteors fall out of a rune circle in the sky.
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
- **Configurable casting balance.** Automatic spell groups, cooldowns and active-cast limits;
  alternative mana, strain and element cooldown modes, with per-spell overrides. The experimental
  system is available on 1.21.1 first — see [BALANCE.md](docs/BALANCE.md).

[↑ Contents](#contents)

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

[↑ Contents](#contents)

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

[↑ Contents](#contents)

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
| K | Shape editor |
| J | Loadout (gear and potion belt) |
| Z / X | Drink a belt potion |

Element keys only take over while magic mode is on. All keys can be rebound.

[↑ Contents](#contents)

## Configuration

Casting balance has a separate `spellweaver-balance.toml` server config. The default uses
short recovery and automatic group cooldowns without mana. Set `mode="ELEMENTS"`, `"MANA"`,
`"STRAIN"`, `"CUSTOM"` or `"OFF"` to change it. All cooldowns, resource costs, regeneration and
exceptions are configurable in game, in the file, or with `/spellweaver config balance.…`.
See [Casting balance](#casting-balance) below for all options, or [BALANCE.md](docs/BALANCE.md)
for additional examples and a test checklist. Creative bypasses balance by default.

Every feature can be tuned or switched off. Don't like mage levels, scrolls from every mob or
spells that break blocks? Turn them off. There are four ways.

**In game.** Press Esc and click the book icon next to *Options…* (it is also next to *Done* in the
Options screen), or go to *Mods → Spellweaver → Config*. The menu shortcut finds free space around other widgets
and follows menu changes. If no free position exists, it hides; the Mods entry remains available. The custom settings screen uses
Spellweaver's dark panels, brass accents and elemental icons. Choose a category on the left,
or search translated option names and config keys. Hover an option for its full description
and default. Lists have a separate editor with add/remove controls. Changes stay in a draft
until you click **Apply**; **Cancel** discards them. Reset a section or restore one option
with its reset button. Invalid values are marked and cannot be applied.

The **/** button beside each option opens its server commands: inspect, set the valid draft
value, or restore the default. Each command has a **Copy** button, including JSON lists of
cooldowns and spell overrides. **/>** beside the category heading opens common server
examples. Copying does not execute commands. In a server console, omit the initial `/`;
running a command saves the change immediately, independently of the settings draft.

The owner can edit a singleplayer world; remote servers require operator permission level 2.
Other players can browse settings without changing them. Apply sends only changed values to
the server, which checks permissions and validates the entire batch before saving and syncing
it. Without a world loaded, the screen shows defaults for reference.

**With a command.** Operators (and singleplayer with cheats on) can change any option on a running server, no restart
needed. Changes are saved to the file and sent to every player at once.

| Command | Action |
| --- | --- |
| `/spellweaver config` | List all options; changed ones are yellow, click a name to edit it |
| `/spellweaver config <option>` | Show the value, description, default and allowed range |
| `/spellweaver config <option> <value>` | Set a value |
| `/spellweaver config <option> default` | Restore the default |

For example, `/spellweaver config instantCast false` brings back cast times for everyone, and
`/spellweaver config mageLevels false` with `/spellweaver config unlockAllSpells true` opens all five
elements and every spell from the start.

**In the file.** Forge 1.19.2/1.20.1 uses `saves/<world>/serverconfig/`
(dedicated server: `world/serverconfig/`). NeoForge 26.3 uses `config/` for synced settings.
On NeoForge 21.1.250, new settings are created in `config/spellweaver-server.toml`
inside the game/server directory. An existing `saves/<world>/serverconfig/spellweaver-server.toml`
(dedicated server: `world/serverconfig/`) overrides the shared file for that world. Older
NeoForge versions can create world-specific files directly. Every option has a comment.
Stop the world/server before editing the loaded file and start it again afterward.
Settings are synchronized to clients.

**Quick switches.** Copy this block into a new `spellweaver-server.toml` and remove `#` from the
lines you want. Put it in `defaultconfigs/` next to `mods/` as a template for missing configs, or edit the
active file described above. Existing configs take precedence. Options you leave out keep their defaults.

```toml
# Spells take time to prepare, as before 1.4.0
#instantCast = false

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
#selfHarm = "OFF"
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
| `instantCast` | true | Spells fire at once; channeled spells last while the button is held |
| `castTimeMultiplier` | 1.0 | Scales every cast time |
| `channelMaxTicksPerElement` | 20 | Channel length per element |
| `playerMagicDamageMultiplier` | 2.0 | Spell damage dealt to players, your own included (0–10) |
| `friendlyFire` | true | Spells can hurt other players |
| `selfHarm` | FULL | Your own blasts, zones and storms hit you: `FULL`, `SCALED` or `OFF` |
| `selfHarmMultiplier` | 0.5 | Self damage when `selfHarm` is `SCALED` |
| `maxActiveSpellsPerPlayer` | 32 | Running spells per player |
| `revivalWindowTicks` | 600 | How long a fallen creature can still be revived |
| `maxUndeadMinionsPerPlayer` | 24 | Undead servants per player |
| `specialEnemyNaturalSpawns` | true | Specialist enemies spawn in the world |

[↑ Contents](#contents)

## Casting balance

This experimental system is currently available on **Minecraft 1.21.1 / NeoForge 21.1.250**.
Open **Mods → Spellweaver → Config**, or use the book button beside Options while playing.
Balance options are grouped under **Spell limits**, **Cooldowns**, **Mana**, **Strain** and
**Active casts**. In Russian these are **«Ограничения»**, **«Перезарядки»**, **«Мана»**,
**«Напряжение»** and **«Активные касты»**. The same screen also covers combat, world,
progression and scroll settings.

On NeoForge 21.1.250, new balance settings are in `config/spellweaver-balance.toml`.
An existing `<world>/serverconfig/spellweaver-balance.toml` (singleplayer:
`saves/<world>/serverconfig/`) overrides that file. Stop the world/server before editing
the active file and restart afterward.
Edit existing keys instead of appending duplicate keys. Server settings are synced to all clients.
See the [settings audit](docs/SETTINGS_AUDIT.md) for application checks and known conditions.

### Balance modes

| `mode` | Behaviour |
| --- | --- |
| `DEFAULT` | Short recovery after casts and shared cooldowns for automatic spell groups; no mana or strain |
| `ELEMENTS` | Cooldowns on the elements used by a formula |
| `MANA` | Initial mana cost, channel drain and delayed regeneration |
| `STRAIN` | Casting builds strain; it decays after a delay; casts exceeding capacity are refused |
| `CUSTOM` | Combine the five `custom…` switches below |
| `OFF` | Disable the new balance restrictions |

| General option | Default | Meaning |
| --- | --- | --- |
| `customRecovery` / `customGroupCooldowns` | true / true | Recovery / group cooldowns in `CUSTOM` |
| `customElementCooldowns` / `customMana` / `customStrain` | false | Other rules in `CUSTOM` |
| `creativeBypass` / `fakePlayerBypass` | true / true | Exempt creative players / automation |
| `showStatus` | true | Show recovery and enabled resources inside the cast HUD card |

Test in survival, or set `creativeBypass=false`. A refused cast preserves the prepared formula
and spends no resource. You can type the next formula during recovery. A channel pays its
initial cost once, then pays per tick until released, exhausted or ended. Cooldowns and
resources persist through death, reconnects and restarts. Switching magic mode off does not
reset them. Existing progression, protection and runtime safety limits apply independently.

### Spell groups and cooldowns

Groups are automatic: **the first matching row wins**, from top to bottom. One resolved spell
has one group shared by its directed, self, area and weapon casts. Five formula points alone
do not make a spell an ultimate. **20 ticks = 1 second.**

| Group | Detected from | Recovery, ticks | Shared cooldown, ticks |
| --- | --- | --- | --- |
| `ultimate` | Ultimate flag or epic channel | 24 | 600 |
| `mining` | Mining effect | 5 | 0 |
| `construction` | Construction shape | 4 | 0 |
| `summon` | Summon shape/mode, raising or summoning undead | 16 | 100 |
| `zone` | Zone/mine shape, zone self mode, hot zone or spawn-zone effect | 16 | 80 |
| `control` | Polymorph, frozen, stunned, rooted or silenced effect | 12 | 40 |
| `healing` | Healing in primary or self effects | 10 | 20 |
| `burst` | Burst projectile, targeted explosion or nova | 14 | 20 |
| `channel` | Other channeled spell | 10 | 0 |
| `utility` | Dash or direct-target spell | 4 | 0 |
| `basic` | Remaining spells | 5 | 0 |

Hydro Erosion belongs to `mining` even though it is channeled. An explosion with a rooting
effect belongs to `control`. Switching to another formula in the same group cannot bypass
its shared cooldown. Recovery restricts all casts. Cooldowns are reserved when a cast is
accepted and renewed when it starts and ends; recovery runs after it ends.

| Cooldown option | Default | Meaning |
| --- | --- | --- |
| `recoveryTicks` | Recovery column above | List of `group=ticks`; omitted group uses 5 ticks |
| `groupCooldownTicks` | Shared cooldown column above | List of `group=ticks`; omitted group uses 0 |
| `cooldownMultiplier` | 1.0 | Scale recovery and group/element cooldowns; 0 disables them |
| `castSpeedAffectsCooldowns` | false | Cast speed also reduces cooldowns |
| `minimumRecoveryTicks` | 2 | Minimum enabled recovery after scaling |
| `elementCooldownTicks` | List below | Per-element ticks; omitted element uses 12 |
| `elementRepeatMultiplier` | 0.25 | Extra cooldown fraction for each repeated point of an element |
| `derivedElementsShareCooldowns` | true | Derived elements also cool down their base parents |

```toml
elementCooldownTicks = ["fire=12", "water=10", "cold=14", "lightning=16", "earth=16", "life=20", "arcane=14", "death=20", "steam=14", "ice=16", "shield=20", "magma=18"]
```

For example, three fire points use `12 × (1 + 2 × 0.25) = 18` ticks before global scaling.
Mixed formulas wait until every participating element is ready. Element cooldowns restrict
casting, not typing. Edit entries in the list editor to change individual groups or elements.

### Mana and strain

Initial cost is `(base + formula points × per-element cost) × group multiplier`. A derived
element occupies one formula point. Channel cost is the per-second value multiplied by the
same group multiplier, paid in twentieths each tick. Disabled resources have no effect.

| Option | Default | Meaning |
| --- | --- | --- |
| `manaCapacity` | 100 | Maximum mana |
| `manaRegenPerSecond` | 12 | Mana restored each second |
| `manaRegenDelayTicks` | 20 | Delay after mana use |
| `manaBaseCost` | 2 | Base initial cost |
| `manaCostPerElement` | 3 | Initial cost per formula point |
| `manaChannelPerSecond` | 8 | Additional channel cost per second |
| `manaGroupMultipliers` | See below | Cost and channel-drain multipliers by group |
| `regenerateWhileChanneling` | false | Allow regeneration while channeling; the delay still applies |
| `refillManaOnDeath` | false | Restore mana after death; cooldowns remain |
| `strainCapacity` | 100 | Maximum strain before casts are refused |
| `strainDecayPerSecond` | 18 | Strain removed each second |
| `strainDecayDelayTicks` | 10 | Delay after strain use |
| `strainBaseCost` | 4 | Base strain per cast |
| `strainPerElement` | 3 | Strain per formula point |
| `strainChannelPerSecond` | 10 | Additional channel strain per second |
| `strainGroupMultipliers` | See below | Initial and channel-strain multipliers by group |

Mana multipliers: `ultimate=4`, `burst=1.5`, `summon=2`, `zone=1.5`, `mining=0.1`,
`construction=0.3`, `utility=0.5`; other groups use 1. Strain uses the same multipliers except
`ultimate=2` and `summon=1.5`. For continuous mana regeneration during channels, enable
`regenerateWhileChanneling` and set `manaRegenDelayTicks=0`. Strain causes no random miscasts.

### Active cast limits

| Option | Default | Meaning |
| --- | --- | --- |
| `activeLimits` | true | Apply active-cast limits in every enabled mode |
| `activeCastLimits` | `["zone=2", "ultimate=1", "summon=3"]` | Concurrent casts by active group; 0 means unlimited |
| `activeLimitPolicy` | `REPLACE_OLDEST` | Retire the oldest cast; use `REJECT` to refuse the new one instead |

A row of zones or several servants from one cast consumes one cast slot. Delayed runtime
effects inherit that slot. Permanent constructions are not removed. Existing companion,
undead and runtime caps remain independent of these limits.

### Per-spell settings

`spellOverrides` defaults to an empty list. Each entry starts with a spell ID, followed by
the fields to replace. For a separate cooldown, give the spell its own group:

```toml
spellOverrides = ["magicka:firebomb|group=firebomb|recovery=10|cooldown=60|mana=20"]
```

This gives Firebomb 0.5 seconds of recovery and its own 3-second cooldown; mana costs 20
only when mana is enabled. Using `group=burst` instead would share the timer with other
burst spells. Supported fields are `group`, `recovery`, `cooldown`, `mana`, `mana_per_second`,
`strain`, `strain_per_second`, `active_group` and `active_limit`. Overrides apply only to
enabled rules. Numeric values must be finite and nonnegative. Group names allow lowercase
letters, digits, dots, underscores and hyphens, up to 64 characters. Custom groups default
to recovery 5, cooldown 0, resource multiplier 1 and active limit 0.

Datapacks can provide the same fields in a recipe's `balance` object. Priority is automatic
defaults → recipe settings → server overrides. See [BALANCE.md](docs/BALANCE.md) for examples.

### Server balance commands

Operators can change settings live; changes are saved and synced to players:

```mcfunction
/spellweaver config balance.mode MANA
/spellweaver config balance.manaCapacity 150
/spellweaver config balance.cooldownMultiplier 1.5
/spellweaver config balance.creativeBypass false
/spellweaver config balance.spellOverrides ["magicka:firebomb|group=firebomb|cooldown=60"]
/spellweaver config balance.mode default
```

Use `/spellweaver config balance.<option>` to inspect an option and append `default` to
restore its factory value. List commands replace the whole list; include every entry you
want to keep. Operators can also use the custom settings screen on remote servers and click
**Apply**. The server checks operator permission again before accepting changes.

For recovery plus mana without shared group cooldowns, choose `CUSTOM`, enable
`customRecovery` and `customMana`, and disable the other three custom switches. To return to
previous unrestricted casting, choose `OFF`.

[↑ Contents](#contents)

## Installation

Requires **NeoForge 26.2.0.57+** for Minecraft **26.2** and Java 25. Install the same version on
the client and the server. No other dependencies.

Spellweaver is available for Minecraft 26.3, 26.2, 26.1 and 1.21.1 on NeoForge, and 1.20.1 and
1.19.2 on Forge. All versions get the same spells and updates.

[↑ Contents](#contents)

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Gametests: `./gradlew runGameTestServer`.

[↑ Contents](#contents)

## License

[MIT](LICENSE). Spellweaver is an independent fan project and is not affiliated with Paradox
Interactive or the Magicka series.
