# Spell balance

The experimental balance system is currently implemented for Minecraft 1.21.1 / NeoForge
21.1.250. Settings belong to the server and apply equally to manual casting and quick slots.
No recipe-by-recipe group assignment is needed.

## Modes

On NeoForge 21.1.250, new files are created in `config/spellweaver-balance.toml`
inside the game or dedicated-server directory. An existing
`saves/<world>/serverconfig/spellweaver-balance.toml` (dedicated server:
`world/serverconfig/spellweaver-balance.toml`) overrides that file for its world.
Edit the loaded file rather than a second copy. Older NeoForge versions may create
world-specific files directly. `defaultconfigs/` supplies templates when the loader
creates a missing config. Stop the world/server before editing files, then start it again.
The active settings are synced to clients.
In game, open **Mods → Spellweaver → Config**, or the book button beside Options while playing.
The custom screen organizes all 37 balance options into Spell limits, Cooldowns, Mana, Strain
and Active casts. Search by translated name or config key. Lists open in a separate editor;
changes stay in a draft until **Apply**. **Cancel** discards the draft. Individual options and
whole sections can be reset. Invalid values cannot be applied.
The singleplayer owner and remote operators (permission level 2) can edit settings. Other
players have read-only access. The server validates all submitted changes before saving;
one invalid value refuses the entire batch. Commands and file editing remain available.
Use **/** beside any option to inspect and copy its view/set/reset commands. Set commands
include the valid draft value and correctly formatted JSON lists. **/>** in the category
header opens common server examples. Copying never executes a command; commands executed
in chat or the server console apply immediately. Omit the initial `/` in the server console.

| `mode` | Rules |
| --- | --- |
| `DEFAULT` | Short recovery after every cast, shared cooldowns for automatic spell groups |
| `ELEMENTS` | Cooldowns on the elements used by a formula |
| `MANA` | Initial mana cost and additional cost while channeling; regeneration after a delay |
| `STRAIN` | Casting builds strain; it decays after a delay; a cast exceeding capacity is refused |
| `CUSTOM` | Combine any of the five `custom…` switches |
| `OFF` | Disable the new balance restrictions |

`activeLimits` applies to every enabled mode. Existing world protection, mage progression,
element locks, summon caps and runtime safety limits still apply independently.
Creative and fake players bypass the new rules by default; disable `creativeBypass` or
`fakePlayerBypass` to include them. Test in survival, or disable the creative bypass.

Operators can change settings live, without a restart:

```mcfunction
/spellweaver config balance.mode MANA
/spellweaver config balance.manaCapacity 150
/spellweaver config balance.creativeBypass false
/spellweaver config balance.groupCooldownTicks ["basic=0", "burst=30", "control=60", "ultimate=800"]
/spellweaver config balance.mode default
```

`default` restores an option's factory value; `DEFAULT` is also the standard mode's name.
List commands replace the entire list. Values for omitted groups use the fallback described
below, so include every group whose value you want to retain.

## Standard mode and automatic groups

20 ticks = 1 second. Group precedence follows this table from top to bottom. Mining and
construction stay useful outside combat. Classification follows the resolved spell's
capabilities, including the effects of generic fusions, rather than a hand-maintained ID list.

| Group | Detected from | Recovery, ticks | Group cooldown, ticks |
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

A spell has one cooldown group shared by its casting intents. Switching between related
formulas does not evade that group's cooldown. Recovery is a separate timer shared by all
casts. Cooldowns are reserved when a cast is accepted and renewed when it starts and ends;
holding a channel cannot consume its cooldown in advance. Recovery starts when it ends.
Switching off magic mode does not reset timers.

You can prepare the next formula during recovery. A rejected cast keeps the prepared formula
and spends no resource. A channel pays its initial cost once and then pays per server tick;
release or resource exhaustion ends it and starts recovery. Damage pulses do not count as
separate casts. Cooldowns and resources persist across reconnects, death and server restarts.
Offline time only advances them while the server's world clock advances.

## Cooldown settings

| Option | Default | Meaning |
| --- | --- | --- |
| `mode` | `DEFAULT` | Preset above |
| `customRecovery` / `customGroupCooldowns` | true / true | Recovery/group rules in `CUSTOM` |
| `customElementCooldowns` / `customMana` / `customStrain` | false | Other rules in `CUSTOM` |
| `recoveryTicks` | Table above | List of `group=ticks`; omitted group: 5 ticks |
| `groupCooldownTicks` | Table above | List of `group=ticks`; omitted group: 0 |
| `cooldownMultiplier` | 1.0 | Scales recovery and group/element cooldowns; 0 disables them |
| `castSpeedAffectsCooldowns` | false | Divide durations by the caster's cast speed / 100 |
| `minimumRecoveryTicks` | 2 | Lower bound on enabled, nonzero recovery after scaling |
| `elementRepeatMultiplier` | 0.25 | Extra cooldown fraction per repeated element point |
| `derivedElementsShareCooldowns` | true | Steam, ice, shield and magma also cool down their parents |
| `creativeBypass` / `fakePlayerBypass` | true | Exempt creative players / automation |
| `showStatus` | true | Compact recovery/resource status inside the cast HUD card |

`elementCooldownTicks` accepts element names and ticks:

```toml
elementCooldownTicks = ["fire=12", "water=10", "cold=14", "lightning=16", "earth=16", "life=20", "arcane=14", "death=20", "steam=14", "ice=16", "shield=20", "magma=18"]
```

Omitted elements use 12 ticks. With a repeat multiplier of 0.25, three fire points use
`12 × (1 + 2 × 0.25) = 18` ticks before global scaling. A mixed formula is available when
all of its participating elements are ready. Element cooldowns restrict casting, not typing.

## Mana and strain

Initial cost is `(base + formula points × per-element cost) × group multiplier`.
Derived elements occupy one formula point each. Channel cost is the per-second setting
multiplied by the same group multiplier, paid in twentieths each server tick.

| Option | Default |
| --- | --- |
| `manaCapacity` | 100 |
| `manaRegenPerSecond` | 12 |
| `manaRegenDelayTicks` | 20 |
| `manaBaseCost` | 2 |
| `manaCostPerElement` | 3 |
| `manaChannelPerSecond` | 8 |
| `regenerateWhileChanneling` | false |
| `refillManaOnDeath` | false |
| `strainCapacity` | 100 |
| `strainDecayPerSecond` | 18 |
| `strainDecayDelayTicks` | 10 |
| `strainBaseCost` | 4 |
| `strainPerElement` | 3 |
| `strainChannelPerSecond` | 10 |

`manaGroupMultipliers` defaults to `ultimate=4`, `burst=1.5`, `summon=2`, `zone=1.5`,
`mining=0.1`, `construction=0.3`, `utility=0.5`. Other groups use 1.
`strainGroupMultipliers` uses the same defaults except `ultimate=2` and `summon=1.5`.
Regeneration and decay delays restart after resource use. To regenerate continuously during
channels, enable `regenerateWhileChanneling` and set `manaRegenDelayTicks=0`.
Strain never causes a random miscast: the server refuses a cast that would exceed capacity.

## Active casts

`activeCastLimits = ["zone=2", "ultimate=1", "summon=3"]` limits ongoing runtime casts.
A fan, row of zones or group of servants created by one cast consumes one slot, not one per
effect node. Delayed effects inherit their original cast's slot. Dead/expired runtime effects
release it. Permanent constructions are not removed by this system.

`activeLimitPolicy="REPLACE_OLDEST"` retires the oldest cast when a new one is accepted.
Use `"REJECT"` to keep existing casts and refuse the new one. `activeLimits=false` disables
these limits; a group limit of 0 means unlimited. Existing elemental companion and undead
entity caps remain independent; raising this new limit does not raise those caps.

## Individual spell overrides and datapacks

Most setups only need the group and element lists. Use `spellOverrides` for exceptions:

```toml
spellOverrides = ["magicka:firebomb|group=burst|recovery=12|cooldown=40|mana=18|strain=12"]
```

Supported fields: `group`, `recovery`, `cooldown`, `mana`, `mana_per_second`, `strain`,
`strain_per_second`, `active_group`, `active_limit`. Numeric values must be finite and
nonnegative. Custom group names use lowercase letters, digits, dots, underscores or hyphens
(maximum 64 characters). A custom group's missing recovery/cooldown/multiplier/limit defaults
to 5/0/1/0. Global cooldown scaling applies after overrides. Each rule only takes effect when
its corresponding subsystem is enabled in the selected mode.

A datapack recipe can supply the same fields in a `balance` object:

```json
"balance": {
  "group": "burst",
  "recovery": 12,
  "cooldown": 40,
  "mana": 18,
  "mana_per_second": 6,
  "active_group": "zone",
  "active_limit": 2
}
```

Precedence: automatic defaults → recipe `balance` → server `spellOverrides`.
Resource overrides are final costs, not group multipliers. Use `active_limit=0` to remove
the new ongoing-cast limit for a specific spell. Invalid numeric override rows are logged
and ignored; invalid recipe values fall back to automatic defaults.

## Examples

Each example belongs in `spellweaver-balance.toml`. Edit existing keys instead of appending
duplicate keys to a generated file.

```toml
# Element cooldown alternative
mode = "ELEMENTS"
elementRepeatMultiplier = 0.15
```

```toml
# Mana alternative
mode = "MANA"
manaCapacity = 150.0
manaRegenPerSecond = 15.0
```

```toml
# Recovery + mana, without shared group cooldowns
mode = "CUSTOM"
customRecovery = true
customGroupCooldowns = false
customElementCooldowns = false
customMana = true
customStrain = false
```

```toml
# Previous unrestricted casting rules
mode = "OFF"
```

## When changes apply

**Apply** and server commands save the active config, refresh spell statistics, progression,
book unlocks and cast/resource status for connected players. New casts use the updated rules.
A zero cooldown multiplier also bypasses timers already running. Disabling a subsystem
removes its gate; enabling it again can expose timers that have not expired yet.
Changing `instantCast` replaces an earlier per-player instant-cast override.

Resource, group and element settings only matter when their subsystem is enabled by the
selected mode. The five `custom…` switches are used only by `CUSTOM`. Creative/fake-player
bypasses can make enabled restrictions appear inactive. `showStatus` changes the HUD only.

Durations and costs already reserved by an ongoing cast are not recalculated retroactively.
Construction lifetimes are assigned when blocks/constructions are created; explicit recipe
lifetimes override material defaults. Existing permanent constructions remain permanent.
Turning off `magicBlockLifetime` stops expiry of existing magic blocks as well.

The settings audit and its limits are documented in [SETTINGS_AUDIT.md](SETTINGS_AUDIT.md).

## Manual test checklist

Use survival or disable `creativeBypass`. In `DEFAULT`, cast a quick basic spell twice,
then try another formula during recovery: preparation should work and an early cast should
keep the formula. Hold/release Hydro Erosion and the arcane beam, then try tapping them
repeatedly. Try switching between two control spells and between two ultimates. Cast enough
zones to replace the oldest, then try `REJECT` policy. Reconnect and die during a long cooldown.
In `MANA`, hold a channel until empty and wait for regeneration. In `STRAIN`, cast repeatedly
until refused and wait for decay. In `ELEMENTS`, cast magma then fire/earth, and test an
unrelated element. Check a quick slot obeys the same restrictions. In `OFF`, confirm the new
restrictions disappear. Check the cast HUD card in Russian and English at different scales.
