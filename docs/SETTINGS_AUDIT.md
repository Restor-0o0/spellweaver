# Settings application audit

Targets: Forge 1.19.2/1.20.1 and NeoForge 1.21.1/26.1/26.2/26.3.
The 1.21.1 reference environment uses NeoForge 21.1.250.

The screen exposes 82 server settings: 45 general settings and 37 balance settings.
Every setting has a consumer in casting, progression, world effects, loot or the HUD.
Some options deliberately depend on another switch or the selected balance mode; see
[BALANCE.md](BALANCE.md#when-changes-apply).

## Automated coverage

| Check | Evidence |
| --- | --- |
| Change every exposed option through the server settings handler | `everySettingChangesAndPersistsThroughServerHandler` changes all 82 and reads their actual loaded TOML files back |
| Validate authorization and reject invalid batches atomically | `SettingsGameTests` covers unauthorized players, invalid numbers/lists, rate limits and command serialization |
| Apply and cancel through the real client screen | `SettingsClientApplicationTest` opens an isolated survival world without cheats and uses the screen widgets and network packets |
| Numeric, boolean and enum editors | The client verifies construction placement, mana capacity, configured cost of an actual cast and synchronized HUD state |
| List editor and resets | The client checks invalid rows, Cancel, Keep without Apply, server Apply, row reset and section reset |
| Persistence after restarting the world | The client leaves and reopens the world, checks saved values and restores the initial settings |
| Spell statistics | Config updates invalidate cached damage/cast-speed bonuses; disabling progression removes bonuses |
| Restrictions and resources | Server tests check cast refusal, subsystem switches, minimum/scaled cooldowns, element sharing, mana/strain costs, channel rates and delayed recovery |
| World and loot | Server tests check construction/freeze toggles, actual material lifetimes, permanent flags, water puddle expiry, registered chest loot, mob/boss chances and dimension bias |
| Player damage | Actual ServerPlayer damage checks player scaling and FULL/SCALED/OFF self harm; deliberate self casting remains separate |

Run the server suite with:

```bat
gradlew.bat runGameTestServer --max-workers=2 -Pneo_version=21.1.250
```

Run the client application audit with:

```bat
gradlew.bat runClient --max-workers=2 -Pneo_version=21.1.250 -PsettingsApplicationTest
```

The client uses `run-settings-audit/`, not the normal game directory or user saves.
The server test writes `test-jars/settings-application-audit.json`, containing one entry
per setting and the exact config file that was saved. Tests restore their starting settings.

## Defects corrected

- Refresh cached spell statistics, mage/formula state, book unlocks and balance HUD state
  after changing settings through either the screen or a command.
- Apply a global instant-cast change over a prior player override; recheck the formula
  length limit when a previously prepared formula is cast.
- Make a zero cooldown multiplier bypass timers already running and hide disabled
  recovery timers in the idle cast HUD.
- Avoid restarting resource regeneration delays when a cast/channel has zero cost.
- Respect disabled magic-block expiry for existing blocks; classify water puddles as water
  so permanent earth does not keep them alive forever.
- Respect disabled construction in direct block placement, freezing water and magma terrain.
- Register the chest scroll loot modifier with its required conditions array.
- Return zero incidental self-damage scaling in the OFF mode.
- Validate command values with the same rules as the screen and reject unknown element names
  instead of silently ignoring them.
- Correct config-path instructions for NeoForge 21.1.250: new files in `config/`, existing
  world `serverconfig/` files override them.

This verifies every option's mutation and persistence, plus focused behavioral cases.
It does not exhaust every combination of settings, spell recipes, other mods or loaders.

## Check persistence across separate server processes

Run these commands from the selected version's checkout:

```bat
gradlew.bat runGameTestServer --max-workers=2 -PsettingsPersistence=write
gradlew.bat runGameTestServer --max-workers=2 -PsettingsPersistence=read
```

For the 1.21.1 reference checkout, also pass `-Pneo_version=21.1.250`.
The write phase saves alternate valid values for all 82 options at server shutdown.
The read phase checks the actual loaded values in a new JVM before test defaults run,
then restores the starting values. Look for `SETTINGS_PERSISTENCE_WRITTEN: 82` and
`SETTINGS_RELOAD_OK: 82` in the respective logs. The probe journal is
`run/settings-persistence-probe.json`; it is only used when this test property is set.
Forge keeps the test world's config files for the read phase and runs the tests on a
different site in that world so existing test constructions do not interfere.
