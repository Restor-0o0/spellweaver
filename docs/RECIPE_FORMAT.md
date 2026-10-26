# Формат рецепта

Minecraft 1.20.1 datapack:

```text
my_spells/
  pack.mcmeta
  data/my_spells/magicka_spell/stone_stair.json
```

`pack.mcmeta`:

```json
{"pack":{"pack_format":48,"description":"Custom Magicka spells"}}
```

`data/my_spells/magicka_spell/stone_stair.json`:

```json
{
  "name": "Quick Stone Stair",
  "tier": "Signature",
  "formula": {"earth": 3, "life": 1},
  "shape": "stair",
  "origin": "caster_feet",
  "cast_ticks": 20,
  "effects": [{"type": "create_construction"}],
  "construction": {
    "material": "earth",
    "budget": 16,
    "interval_ticks": 3,
    "lifetime_ticks": 1200,
    "integrity": 12,
    "floating": false
  },
  "primary": "Лестница от ног с ускоренным ростом.",
  "self_description": "Оболочка, замедление и регенерация — стандартные Earth/Life эффекты."
}
```

Файл из другого namespace заменяет базовый рецепт с той же формулой. Можно также переопределить исходный `data/magicka/magicka_spell/<id>.json` обычным приоритетом datapack. Два пользовательских рецепта с одинаковой формулой в разных файлах считаются ошибкой. Формула всегда содержит 1–4 элемента; неизвестные стихии, эффекты и некорректные числа отвергаются. Пропущенные базовые формулы сохраняют стандартное поведение.

## Основные поля

| Поле | Значение |
| --- | --- |
| `formula` | Количества `fire`, `water`, `cold`, `lightning`, `earth`, `life`, `arcane` |
| `name`, `tier`, `tags`, `primary`, `self_description` | Тексты книги |
| `shape` | `projectile`, `burst_projectile`, `beam`, `spray`, `chain`, `targeted_explosion`, `targeted_zone`, `nova`, `platform`, `pillar`, `wall`, `bridge`, `stair`, `ramp`, `growth`, `direct_target`, `dash`, `summon_object`, `mine` |
| `origin` | `caster`, `caster_feet`, `caster_front`, `aim_hit`, `aim_block`, `aim_point`, `target_entity`, `under_target`, `corpse_target`, `aim_surface`, `aim_ground` |
| `cast_ticks` | Подготовка 1–200 тиков; затем применяется серверный множитель |
| `effects` | Непустой граф Primary |
| `self.mode` | `generic`, `effects`, `nova`, `dash`, `zone`, `summon`, `construct`, `meteor` |
| `self.effects` | Граф Self; если отсутствует — стандартные элементальные Self-эффекты |

Точка появления выбирается с учётом формы: снаряд стартует перед глазами, луч — из рук к прицелу; конструкции используют origin, включая специальное начало лестницы перед ногами. У Nova и Self центром служит caster. Попадание по сущности уточняет позицию цели для направленных действий. `aim_hit`, `aim_block`, `aim_surface`, `aim_ground` используют результат трассировки мира; `aim_point` допускает точку в воздухе.

## Доставка и строительство

`delivery`: `radius`, `speed`, `gravity`, `duration_ticks`, `interval_ticks`, `burst_radius`, `delay_ticks`, `shards`, `dash_speed`, `object_kind` (`healer`, `bubble`, `anchor`), `object_health` (1–20). Флаги `meteor`, `volatile`, `slow_projectiles`, `conductive`, `ice_surface`, `heal_weakest` включают соответствующее поведение; для выключения флаг удалить.

`construction`: `material` (`earth`, `ice`, `root`, `arcane`), `budget` (1–64, ограничивается конфигом), `interval_ticks` (1–40), `width` (1–5 для платформ), `height` (1–5 для стен), `lifetime_ticks` (20–72000), `integrity`, `floating`, `regenerates`. Stair/Ramp — последовательность смежных сегментов, ширина этих форм фиксирована. Динамический рост останавливается после исчерпания попыток, даже если часть позиций была запрещена.

## Узлы графа

| `type` | Параметры |
| --- | --- |
| `deal_damage` | `amount`, `element` — fire/lightning/earth учитывают реакции |
| `heal` | `amount` |
| `apply_status` | `status`, `duration_ticks`, `strength` |
| `remove_status` | `status` |
| `set_wet` | `duration_ticks` |
| `modify_temperature` | `amount`: положительное — Heat, отрицательное — Cold |
| `add_charge` | `amount` |
| `apply_force` | `amount`, `lift` |
| `destroy_blocks` | `budget`, `radius` |
| `brittle_terrain` | `radius` |
| `grow_plants` | `budget` |
| `chain_targets` | `count`, `range`, `amount` |
| `spawn_projectile` | Использует delivery текущего рецепта |
| `spawn_zone` | `duration_ticks`, `radius`; повторяет граф текущего рецепта |
| `create_construction` | Использует construction текущего рецепта |
| `delay` | `ticks`, вложенный массив `effects` |
| `conditional` | `condition`: `wet`, `was_frozen`, `brittle`, `self`; вложенный `effects` для сущности |
| `revive` | `health_fraction` |

Статусы: `wet`, `burning`, `chilled`, `frozen`, `shocked`, `stunned`, `brittle`, `rooted`, `regenerating`, `arcane_unstable`, `stone_shell`, `levitating`, `slow_falling`, `haste`, `obscured`.

Граф выполняется последовательно. Урон одного попадания суммируется перед вызовом Minecraft damage API. Узлы мира исполняются один раз на попадание, а не на каждую сущность. Периодические зоны не создают дочерние зоны/снаряды/конструкции рекурсивно. Глубина вложения ограничена шестью уровнями, число узлов в массиве — 64; активные экземпляры ограничены сервером.
