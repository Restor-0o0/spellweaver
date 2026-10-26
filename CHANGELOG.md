# Changelog

## 1.4.0 — the mage's path

- Mage level: casting, magic kills and reading new scrolls give mage experience. New mages weave three-element formulas; four elements open at level 3, five at level 8. Each level adds 1% cast speed and 1% spell damage. Creative mode is never limited.
- Any hostile mob from any mod can drop a spell scroll (0.5%; witches, evokers and other spellcasters 5%). Mobs with 150+ max health count as bosses and always drop one.
- Nether scrolls lean towards fire, End scrolls towards arcane, and both carry more ultimates.
- Mobs have elemental natures: fire-immune mobs resist fire and fear water and cold, undead resist death and suffer life magic, aquatic mobs resist water and conduct lightning, End creatures resist arcane. Tunable with `magicka:resists/<element>` and `magicka:weak/<element>` entity tags.
- Dungeon mod support: bosses of L_Ender's Cataclysm, Mowzie's Mobs and Aquamirae count as bosses and have their own resistances; chests of When Dungeons Arise, YUNG's Better Dungeons and other structure mods can hold scrolls.
- "Mage Path" advancements, from the first cast to the archmage rank.
- The Grimoire shows your mage level, experience and scrolls read.
- Every feature can be tuned or switched off in game: click the book icon next to Options in the pause menu or open the mod settings from the Mods list. Every option in `spellweaver-server.toml` now has a description, and the README has ready-made switches to uncomment.
- Instant cast is now on by default and lives in the mod settings (`instantCast`) instead of the H key.
- The formula panel shows only as many cells as your mage level allows; pressing an extra element just fizzles.
- `/spellweaver config` lists and changes any setting on a running server, without a restart; changes reach every player at once.
- Lower minimum NeoForge version.
- Spells reach as far as you can aim: only the flamethrower spray keeps a short range. Epic spells reach 160 blocks, Piercing Rays, Black Flame, Beckon and Spirit Wave 64.
- Blizzard drops chunks of ice and snow that shatter before landing. Deluge sends a real wave of water across the ground.
- Volcanic Vent splits the ground into a magma fissure along your gaze. Grasping Dead shows hands clawing out of the earth and hits twice as hard.
- Undead servants rise out of the ground instead of appearing in place.
- Piercing Rays: every ray now lands (before, only two of five did), aims at the creature you look at and deals 2 ❤ per ray.
- Brittle Cage and other growth spells grow in rings. Field of Flowers covers 12 blocks. Explosion is bigger.
- Two new spells: Meadow (Water, Earth, Life ×2) grows grass and ferns, and Grove (Earth ×2, Life ×3) grows up to 6 trees of the local biome at once.
- Rune circles: every spell has its own rune, written from 24 element glyphs. Spells of three or more elements flash it at your hand, larger for longer formulas; a held channel keeps it steady in front of you; ultimates light three stacked circles over the target. Area spells mark their zone with it.
- Meteors fall out of a rune circle in the sky and the storm hangs a circle under its cloud. Titans rise from a rune circle, elementals step out of one, and Spark Portal gates are rune circles.
- Piercing Rays fire from five rune circles that follow you until the last ray. Cast on yourself, it raises a defensive rune circle for 5 s that stops arrows and blocks every hit from the front.
- Deluge rolls across the aimed spot instead of starting at your feet.
- Spark Portal stays open for 30 s, reaches up to 192 blocks, and its far gate opens on the wall or ceiling you aim at instead of dropping to the floor.
- Brittle Cage and other growth spells centre on the creature you aim at; the cage now has a roof.
- Silver Stag gallops for 8 s without stopping, turning in smooth arcs from foe to foe and ramming them aside.
- Spark Portal never drops you into a wall. Quicksilt now catches the caster too.
- Renamed: Root Spire, Floating Crystal, Rune Bastion.

## 1.3.1 — scrolls from enemies

- Specialist enemies now sometimes drop spell scrolls (4%; casters such as countermages, element binders, goblin mages and shamans 10%). They never drop ultimates.
- Bosses always drop a scroll, and ultimates are three times as likely as other spells.
- Enemy drops prefer a scroll the killer has not read yet.

## 1.3.0 — scrolls and potions

- Spell scrolls: elementals, ultimates, legendary and the strongest signature spells are sealed in survival until you read their scroll. A sealed formula casts the spell one element shorter, and sealed spells are hidden in the grimoire. Scrolls are never used up and are found in structure chests. Creative mode and the `unlockAllSpells` server option open everything.
- Potion belt: six potion slots on the loadout screen (J). Two belt keys (Z and X) each drink their own chosen potion; hold a key to pick its potion on a wheel. Drinking takes about half a second and clears the prepared formula, and the empty bottle comes back.
- Seven new potions, brewed from Awkward Potion and extended with redstone: five elemental wards (fire, cold, lightning, arcane, death; 40% less damage of that element), Swift Casting (+25% cast speed) and Spell Focus (+20% spell damage). Splash and lingering versions work as usual.

## 1.2.0 — gear that changes spells

- Every gear item can now be crafted in survival.
- Gear can carry its own rolled bonuses (stats, traits, spell rules) on the item stack.
- 17 new spell-changing items: fanned projectiles, ricocheting beams, zones that follow you, burning ground, extra summons, formula swaps (water boils into fire), a shard that whispers Arcane into every spell, earth constructions that last twice as long and more.
- 8 named legendary items.
- 11 charms: socket them into gear or any vanilla tool, weapon or armor on an anvil; strip them on a grindstone.
- New traits: Quickening (instant cast after a kill), Desperate Mending, Shared Blessing, Resonance (every 5th cast echoes) and Adaptation (elemental hits attune you to that element).

## 1.1.0 — first public release

- 8 elements, up to five per formula: all 1286 combinations are castable.
- 204 signature spells in the Grimoire, including 19 ritual-ordered ultimates and elemental titans.
- 32 legendary spells: Spirit Wave, Spiral Sphere, Time Stop, Great Aegis, the five elemental signs and more.
- 18 specialist enemies, elementals and gear.
- Instant cast mode, quick-cast slots and the 5×5×5 shape editor.
- Server config for terrain damage, friendly fire, construction and spawns.
