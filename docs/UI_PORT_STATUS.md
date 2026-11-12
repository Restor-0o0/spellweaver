# UI port status

Source: current uncommitted 1.21.1 HUD editor, grimoire and spell icons, with the prefix search from 26.3.

- 1.21.1 / NeoForge 21.1.250: Gradle build successful; installed in magicka_test.
- 1.20.1: HUD classes, book layout, name/ID prefix search, current icons, HUD entry from the book, arcane flow and reduced mining effects adapted; Gradle build successful.
- 1.19.2: Same feature set adapted through the legacy GUI and vertex APIs; Gradle build successful.
- 26.1: Same feature set adapted; Gradle build successful. HUD rendering uses ordered card passes and per-color opacity.
- 26.2: Same feature set adapted; Gradle build successful. Screen and camera access use the target APIs.
- 26.3: Current HUD, book layout, prefix search and icons synchronized; reduced mining effects applied. Existing beam tracking and flow renderer retained. Gradle build successful.

All six targets have identical Russian and English localization values (2626 keys each), type glyphs and steam/ice glyphs. Composite element pages and filter masks include magma on every target. Book quick assignment remains four slots; HUD quick slot count is configurable separately. UI behavior still needs manual in-game verification on each target.

The complete 2626-key catalogue is also localized into German, Spanish, French,
Brazilian Portuguese, Simplified Chinese and Japanese. The same eight locale files
are installed on every target. See LOCALIZATION.md for translation and validation details.

ElementIcons is the shared renderer for element filters, book formula sockets, composite pages, dragged formulas and HUD formula icons. Vanilla item pictograms for elements have been removed from MagicUi. Steam, ice, shield and magma use the same custom glyphs and backgrounds everywhere.

UiText is the shared bounded text renderer on all six targets. Buttons and headings
fit both dimensions and center after scaling; paragraphs select the largest fitting
scale and preserve every line. The book, HUD, HUD editor, gear and shape screens and
potion wheel use it. New labels should supply their content rectangle to UiText
instead of measuring or truncating text at a fixed font size.

No release tags created. Updated jars in magicka_test, magicka_test_19_2, magicka_test_20_ and magicka_test_26_3. No matching test instances found for 26.1 or 26.2. The complete test set and SHA-256 manifest are in test-jars/current. Archive integrity and installed jar hashes were verified by tools/distribute_test_jars.py --copy.
