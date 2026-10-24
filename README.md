<p align="center"><img src="src/main/resources/spellweaver.png" alt="Spellweaver" width="160"></p>

# Spellweaver (Magicka-like spellcasting)

Magicka-style elemental spellcasting for **Minecraft 1.21.1 · NeoForge**. No wands, no mana bars
to grind: press up to five elements, then cast. Every combination does something.

*Русское руководство: [docs/GUIDE_RU.md](docs/GUIDE_RU.md)*

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
- **Gear** that tunes your element powers, area, duration, summons and cast speed.
- **Datapack spells.** Any recipe can be added or replaced — see [RECIPE_FORMAT.md](docs/RECIPE_FORMAT.md).

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

Element keys only take over while magic mode is on. All keys can be rebound.

## Installation

Requires **NeoForge 21.1.250+** for Minecraft **1.21.1** and Java 21. Install the same version on
the client and the server. No other dependencies.

Server settings (terrain damage, friendly fire, construction limits, block lifetimes, natural enemy
spawns and more) live in `serverconfig/spellweaver-server.toml`.

## Building

```
./gradlew build
```

The jar lands in `build/libs/`. Gametests: `./gradlew runGameTestServer`.

## License

[MIT](LICENSE). Spellweaver is an independent fan project and is not affiliated with Paradox
Interactive or the Magicka series.
