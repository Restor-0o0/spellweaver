# Localization

English (`en_us`) is the source locale. Russian (`ru_ru`) is maintained separately.
Additional locales: German (`de_de`), Spanish (`es_es`), French (`fr_fr`), Brazilian
Portuguese (`pt_br`), Simplified Chinese (`zh_cn`) and Japanese (`ja_jp`).

The additional locales begin with machine translations of the complete catalogue,
including spell names, detailed descriptions, guides, items and configuration.
Core element names and compact interface labels are manually maintained in
`tools/localization/common.json`. Its columns follow the locale order above.
Optional locale-specific key overrides belong in `tools/localization/<locale>.json`.
These translations still need review by native speakers, especially spell flavour text.

`python tools/generate_locales.py` rebuilds the additional locale files from the
local cache in `test-jars/localization-cache` and applies manual overrides.
`--fetch` requests missing line translations from Google Translate. This sends the
English game text to an external service and requires the user's permission.
The local cache is not needed to build or run the mod.

The generator protects format arguments, escaped percent signs, numeric values,
key names, resource identifiers and book layout markers before translation.
`python tools/audit_localization.py` checks every locale and version for missing
or duplicate keys, invalid placeholders, leaked translation markers and lost guide
headings. `python tools/sync_locales.py` copies the complete locale set to the five
version worktrees and verifies byte-for-byte equality.

Select the language in Minecraft's language settings. UiText scales labels to both
the width and height of their content bounds and centers the final scaled result.
Book descriptions wrap and shrink to the available page area without dropping the
last lines. The book, HUD, HUD editor, equipment and shape editor use this renderer.
