# Spellweaver (magicka) — project rules

Minecraft NeoForge mod: Magicka-style elemental spellcasting. Eight elements combine into
1286 formulas (346 hand-made + rule-based fusion); grimoire with search/tags/quick-cast
slots, rune circles, tiers, summons, constructions, datapack recipes.

## Current target
For this project workflow, the default target and manual test environment are Minecraft
`1.21.1` with NeoForge `21.1.250`, using `%APPDATA%\.minecraft\versions\magicka_test`.
All fixes should be implemented and verified against that environment unless the user
explicitly names another Minecraft/NeoForge version. The current `gradle.properties`
still describes the separate `26.3` source target; do not silently treat that as the
default test target for this collaboration.

## Code style
- Almost no comments. No comments about bugs, tickets or what changed.
- Match the surrounding naming/style/pattern.
- Keep server logic authoritative and deterministic per formula; client screen/UI code stays
  on the client side.
- Do not add unrelated refactors to a spell/balance fix.

## Build and verify
- Build the version currently requested by the user; default to the 1.21.1/NeoForge
  21.1.250 test setup described in `PACK.md`.
- After a successful build, copy the resulting `magicka-*.jar` into the test instance's
  `mods` directory at `%APPDATA%\.minecraft\versions\magicka_test\mods` before manual
  testing. Preserve unrelated mods and remove/replace only the previous `magicka` jar.
- Server tests: `./gradlew runGameTestServer` when available for the selected target.
- Client check: launch the dev client, open the grimoire (`B`) and the HUD; check Russian
  and English strings after UI changes.
- Test instance for manual play: `%APPDATA%\.minecraft\versions\magicka_test`.
  Pack scripts: `python tools/pack_download.py`, `python tools/pack_configure.py`,
  `python tools/pack_launch.py` (see `PACK.md`).

## Docs
- `README.md` — features, controls, config, install, build.
- `docs/GAME_DESIGN.md`, `docs/SPELLS.md` (full spell catalog), `docs/MAGIC_MECHANICS.md`,
  `docs/RECIPE_FORMAT.md`, `docs/ENEMIES.md`, `docs/ROADMAP.md`,
  `docs/MEGADUNGEON_PACK.md`, `docs/GUIDE_RU.md`.
- `Minecraft_Magicka_All_329_Recipes_v1.0.md` and
  `Minecraft_Magicka_Implementation_Spec_v1.0.md` — original specs (historically 329
  formulas; the current build has 1286).
- `PACK.md` — test modpack, scripts and known-bad mods.
- `CODEX_SESSION.md` — history of the earlier implementation sessions.
- Project skills: none.
