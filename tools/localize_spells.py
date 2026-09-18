"""Build RU/EN names and descriptions for every spell from its actual effect data.

Run after import_spell_catalog.py and add_death_spells.py. Writes spell.magicka.* keys into both
lang files and replaces the JSON primary/self_description with the generated English text.
"""
import json, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / 'src/main/resources/data/magicka/magicka_spell'
LANG = ROOT / 'src/main/resources/assets/magicka/lang'
SHORT_RU = {'F': 'О', 'W': 'В', 'C': 'Х', 'L': 'М', 'E': 'З', 'Li': 'Ж', 'A': 'А'}

NAMES = {
    'Curse': 'Проклятие',
    'Hex': 'Хекс',
    'Magnetic Lash': 'Магнитный хлыст',
    'Overgrowth': 'Заросли',
    'Shatter Bolt': 'Раскол',
    'Sheep': 'Овца',
    'Sheepocalypse': 'Овцепокалипсис',
    'Flare': 'Фальшфейер',
    'Soul Chain': 'Цепь душ',
    'Thorn Spike': 'Терновый шип',
    'Firestorm': 'Огненная буря',
    'Deluge': 'Потоп',
    'Absolute Zero': 'Абсолютный ноль',
    'Thunderstorm': 'Гроза',
    'Earthquake': 'Землетрясение',
    'Font of Life': 'Источник жизни',
    'Singularity': 'Сингулярность',
    'Apocalypse': 'Апокалипсис',
    'Arc Lightning': 'Дуговая молния', 'Arcane Beam': 'Арканный луч', 'Arcane Bolt': 'Арканная стрела',
    'Arcane Detonation': 'Арканный взрыв', 'Arcane Lance': 'Арканное копьё', 'Arcane Meteor': 'Арканный метеор',
    'Arcane Pulse': 'Арканный импульс', 'Bolt': 'Разряд', 'Boulder': 'Валун', 'Brittle Cage': 'Хрупкая клетка',
    'Bubble Lift': 'Пузырь-подъёмник', 'Buoyant Bubble': 'Парящий пузырь', 'Cautery': 'Прижигание', 'Chill': 'Холодок',
    'Conductive Flood': 'Проводящий потоп', 'Conductive Ice': 'Проводящий лёд', 'Creeper Graves': 'Могилы криперов',
    'Cryostasis': 'Криостазис', 'Crypt Husks': 'Кадавры склепа', 'Crystal': 'Кристалл', 'Crystal Anchor': 'Кристальный якорь',
    'Crystal Frost': 'Кристальный иней', 'Death Ray': 'Луч смерти', 'Deep Freeze': 'Глубокая заморозка', 'Doom': 'Рок',
    'Drowned Rising': 'Восстание утопленников', 'Ember': 'Уголёк', 'Fire Nova Bomb': 'Огненная нова-бомба',
    'Firebolt': 'Огненная стрела', 'Firebomb': 'Огненная бомба', 'Flame Lance': 'Огненное копьё',
    'Flash Freeze Lake': 'Мгновенный лёд', 'Floating Platform': 'Парящая площадка', 'Flooded Circuit': 'Затопленная цепь',
    'Fork': 'Развилка', 'Freezing Ray': 'Замораживающий луч', 'Frost Bolt': 'Ледяная стрела', 'Frostflow': 'Ледяной поток',
    'Frostshock': 'Ледяной разряд', 'Hoarfrost': 'Изморозь', 'Frozen Host': 'Ледяное воинство',
    'Glacier Hammer': 'Ледниковый молот', 'Grasping Dead': 'Хватка мертвецов', 'Grave Call': 'Зов могилы',
    'Grave Chill': 'Могильный холод', 'Greater Mend': 'Великое исцеление', 'Guardian Stone': 'Камень-хранитель',
    'Haste Surge': 'Прилив скорости', 'Heal': 'Лечение', 'Healing Pool': 'Целебная лужа', 'Healing Spring': 'Целебный источник',
    'Hellfire Skull': 'Череп адского огня', 'Hibernating Roots': 'Спящие корни', 'Ice Spear': 'Ледяное копьё',
    'Ice Spike': 'Ледяной шип', 'Ice Step': 'Ледяная ступень', 'Inferno': 'Инферно', 'Jet': 'Напор',
    'Legion of the Dead': 'Легион мёртвых', 'Living Bridge': 'Живой мост', 'Living Bulwark': 'Живой бастион',
    'Living Wall': 'Живая стена', 'Magma': 'Магма', 'Magma Boulder': 'Магмовый валун', 'Mend': 'Исцеление',
    'Mend Burst': 'Вспышка исцеления', 'Molten Boulder': 'Раскалённый валун', 'Moving Bastion': 'Каменный лифт',
    'Mud': 'Грязь', 'Overcharge': 'Перегрузка', 'Pebble': 'Галька', 'Permafrost': 'Вечная мерзлота',
    'Phoenix Leap': 'Прыжок феникса', 'Plague Mist': 'Чумной туман', 'Plasma': 'Плазма', 'Plasma Detonation': 'Плазменный взрыв',
    'Plasma Orb': 'Плазменная сфера', 'Preservation Field': 'Поле сохранения', 'Quicksilt': 'Зыбучий ил',
    'Raise Dead': 'Поднятие мёртвых', 'Regenerate': 'Регенерация', 'Regeneration Mist': 'Целебный туман',
    'Resurrection': 'Воскрешение', 'Rock': 'Камень', 'Climbing Roots': 'Вьющиеся корни', 'Rootstone': 'Корнекамень',
    'Sanctuary': 'Святилище', 'Shock Field': 'Шоковое поле', 'Stone Sculpture': 'Каменная форма', 'Skybridge': 'Небесный мост',
    'Soul': 'Душа', 'Soul Return': 'Возвращение души', 'Soul Siphon': 'Похищение души', 'Soul Storm': 'Буря душ',
    'Spark': 'Искра', 'Splash': 'Всплеск', 'Spring': 'Родник', 'Stasis': 'Стазис', 'Stasis Field': 'Поле стазиса',
    'Static Stasis': 'Статический стазис', 'Steam': 'Пар', 'Steam Mine': 'Паровая мина', 'Stone Platform': 'Каменная площадка',
    'Stone Stair': 'Каменная лестница', 'Storm Puddle': 'Грозовая лужа', 'Stormwater': 'Грозовая вода',
    'Suspension': 'Парящая вода', 'Tesla Spire': 'Башня Теслы', 'Thermal Fracture': 'Термический разлом',
    'Thermal Shock': 'Термоудар', 'Thunder Lance': 'Громовое копьё', 'Thunder Nova': 'Громовая нова',
    'Thunderflame': 'Громовое пламя', 'Thunderstone': 'Громовой камень', 'Tomb Collapse': 'Обвал склепа',
    'Torrent': 'Поток', 'Vital Spark': 'Искра жизни', 'Volatile Magma Core': 'Нестабильное ядро магмы',
    'Volcanic Vent': 'Вулканический разлом', 'Wisp Light': 'Блуждающий огонёк', 'Lantern Trail': 'Тропа огоньков',
    'Glowstone': 'Светокамень', "Cat's Eye": 'Кошачий глаз', 'Gills': 'Жабры', 'Fire Ward': 'Огнеупорность',
    'Stone Wall': 'Каменная стена', 'Stone Slab': 'Каменная плита', 'Slanted Wall': 'Наклонная стена', 'Ice Wall': 'Ледяная стена', 'Earthen Redoubt': 'Земляной редут',
    'Revealing Flash': 'Озаряющая вспышка', 'Douse': 'Тушение', "Miner's Vigor": 'Сила рудокопа', 'Water Cannon': 'Водомёт', 'Witchfire': 'Ведьмин огонь', 'Wraith Swarm': 'Рой призраков',
}
CORES = {
    'Frostshock': ('Ледяной разряд', 'm'), 'Plasma': ('Плазма', 'f'), 'Stormwater': ('Грозовая вода', 'f'),
    'Thunderstone': ('Громовой камень', 'm'), 'Arc Lightning': ('Дуговая молния', 'f'), 'Vital Spark': ('Искра жизни', 'f'),
    'Frostflow': ('Ледяной поток', 'm'), 'Mud': ('Грязь', 'f'), 'Spring': ('Родник', 'm'), 'Steam': ('Пар', 'm'),
    'Suspension': ('Парящая вода', 'f'), 'Cryostasis': ('Криостазис', 'm'), 'Thermal Shock': ('Термоудар', 'm'),
    'Permafrost': ('Вечная мерзлота', 'f'), 'Stasis': ('Стазис', 'm'), 'Magma': ('Магма', 'f'), 'Soul': ('Душа', 'f'),
    'Witchfire': ('Ведьмин огонь', 'm'), 'Crystal': ('Кристалл', 'm'), 'Cautery': ('Прижигание', 'n'),
    'Rootstone': ('Корнекамень', 'm'), 'Mend': ('Исцеление', 'n'),
}
ADJECTIVES = {
    'Charged': ('Заряженный', 'Заряженная', 'Заряженное'), 'Flooded': ('Полноводный', 'Полноводная', 'Полноводное'),
    'Frozen': ('Промёрзший', 'Промёрзшая', 'Промёрзшее'), 'Heavy': ('Тяжёлый', 'Тяжёлая', 'Тяжёлое'),
    'Living': ('Живой', 'Живая', 'Живое'), 'Scorching': ('Палящий', 'Палящая', 'Палящее'),
    'Unstable': ('Нестабильный', 'Нестабильная', 'Нестабильное'), 'Greater': ('Великий', 'Великая', 'Великое'),
}

FLAVOR = {
    'firestorm': ('Небо горит: огонь падает в выбранную точку тринадцать секунд подряд.', 'The sky burns: fire keeps falling on the chosen spot for thirteen seconds.'),
    'deluge': ('Стена воды смывает всё перед вами и тушит любой пожар.', 'A wall of water sweeps everything in front of you away and puts out any fire.'),
    'absolute_zero': ('Всё в радиусе пяти блоков промерзает насквозь и больше не двигается.', 'Everything within five blocks freezes solid and stops moving.'),
    'thunderstorm': ('Молнии перескакивают с врага на врага, пока не кончатся цели.', 'Lightning jumps from enemy to enemy until it runs out of targets.'),
    'earthquake': ('Земля встаёт дыбом: порода крошится, а все внутри прижаты к земле.', 'The ground rears up: rock crumbles and everyone inside is pinned down.'),
    'font_of_life': ('Родник жизни лечит союзников пятнадцать секунд и поднимает павших на ноги.', 'A spring of life heals allies for fifteen seconds and puts the fallen back on their feet.'),
    'singularity': ('Точка схлопывающегося пространства стягивает всё живое к себе и рвёт на части.', 'A point of collapsing space drags everything alive inward and tears it apart.'),
    'apocalypse': ('Целая неживая армия встаёт рядом и служит вам минуту.', 'A whole undead army rises beside you and serves you for a minute.'),
    'stone_wall': ('Стена от стрел, а если посмотреть вверх — шатёр от атак с неба.', 'A wall against arrows, or look up for a tent against attacks from the sky.'),
    'stone_slab': ('Плита, которая станет полом под ногами или крышей над головой.', 'A slab that becomes a floor under your feet or a roof over your head.'),
    'slanted_wall': ('Закрывает и спереди, и сверху: стрелы и огненные шары фантомов бьются о камень.', 'Covers you from the front and above: arrows and fireballs from the sky hit the stone.'),
    'ice_wall': ('Быстрое укрытие, которое само растает.', 'Quick cover that melts away on its own.'),
    'earthen_redoubt': ('Полукольцо земли прикрывает спереди и с боков.', 'A half ring of earth covers your front and flanks.'),
    'wisp_light': ('Маленький огонёк, который остаётся там, куда долетел.', 'A tiny light that stays wherever it lands.'),
    'lantern_trail': ('Отмечает дорогу в пещере цепочкой огоньков.', 'Marks your way through a cave with a chain of lights.'),
    'glowstone': ('Магический светокамень вместо факела.', 'A magic glowstone instead of a torch.'),
    'cats_eye': ('Темнота больше не мешает.', 'Darkness no longer gets in the way.'),
    'gills': ('Дышать под водой и плавать как дельфин.', 'Breathe underwater and swim like a dolphin.'),
    'fire_ward': ('Тушит и защищает от огня и лавы.', 'Puts out flames and protects from fire and lava.'),
    'revealing_flash': ('Вспышка показывает всех врагов вокруг сквозь стены.', 'A flash that shows every enemy around through walls.'),
    'douse': ('Гасит пожар вокруг точки прицела.', 'Puts out a blaze around the aim point.'),
    'miners_vigor': ('Копать быстрее и видеть в шахте.', 'Dig faster and see in the mine.'),
    'arc_lightning': ('Сгусток арканного электричества, который сам ищет следующую жертву.', 'A bolt of arcane electricity that seeks out its next victim.'),
    'arcane_beam': ('Сфокусированный поток чистой арканы.', 'A focused stream of raw arcane power.'),
    'arcane_bolt': ('Быстрая арканная стрела для точных ударов.', 'A fast arcane bolt for precise strikes.'),
    'arcane_detonation': ('Арканный взрыв с мощной отдачей: разбрасывает врагов.', 'An arcane blast with a heavy shockwave that scatters enemies.'),
    'arcane_lance': ('Мгновенный арканный луч, который взрывается в конце.', 'An instant arcane beam that detonates where it ends.'),
    'arcane_meteor': ('Над целью собирается каменная глыба и рушится вниз.', 'A boulder gathers above the target and crashes down.'),
    'arcane_pulse': ('Простейший арканный заряд.', 'The simplest arcane charge.'),
    'bolt': ('Молния срывается с руки в цель и перескакивает на соседей.', 'Lightning leaps from your hand into the target and jumps to those nearby.'),
    'boulder': ('Тяжёлый валун, который крушит и отбрасывает.', 'A heavy boulder that smashes and knocks back.'),
    'brittle_cage': ('Ледяная клетка вокруг цели держит её на месте.', 'An ice cage grows around the target and holds it in place.'),
    'bubble_lift': ('Пузыри поднимают всех в области в воздух.', 'Bubbles lift everyone in an area into the air.'),
    'buoyant_bubble': ('Пузырь бережно поднимает одну цель и смягчает падение.', 'A bubble gently lifts one target and softens its fall.'),
    'cautery': ('Лечит огнём: закрывает раны, но обжигает.', 'Heals with fire: seals wounds but burns.'),
    'chill': ('Лёгкий холод, замедляющий цель.', 'A light chill that slows the target.'),
    'conductive_flood': ('Затопляет участок и бьёт током всех, кто стоит в воде.', 'Floods an area and shocks everyone standing in the water.'),
    'conductive_ice': ('Покрывает землю льдом, который проводит разряды.', 'Covers the ground with ice that conducts lightning.'),
    'creeper_graves': ('Крипера-слуги бегут к врагам и взрываются. Взрыв задевает всех рядом — и других слуг, и хозяина.', 'Creeper servants run at enemies and explode. The blast hits everyone nearby, including other servants and their master.'),
    'cryostasis': ('Холод и жизнь: замедляет, но лечит и защищает.', 'Cold and life: slows, but heals and protects.'),
    'crypt_husks': ('Кадавры не горят на солнце и насылают голод.', 'Husks do not burn in sunlight and inflict hunger.'),
    'crystal': ('Острый кристалл из земли и арканы.', 'A sharp shard of earth and arcane.'),
    'crystal_anchor': ('Выращивает большой висящий в воздухе кристалл — опору для построек.', 'Grows a large floating crystal to anchor constructions.'),
    'crystal_frost': ('Небольшой висящий кристалл, на который можно встать.', 'A small floating crystal you can stand on.'),
    'death_ray': ('Луч смерти вытягивает жизнь, пока вы его держите.', 'A ray of death that drains life while you hold it.'),
    'deep_freeze': ('Ледяной снаряд, замораживающий всех вокруг места попадания.', 'An icy shell that freezes everyone around where it lands.'),
    'doom': ('Метка смерти: через 3 секунды цель получает огромный урон.', 'A mark of death: after 3 seconds the target takes massive damage.'),
    'drowned_rising': ('Утопленники выходят из земли и сражаются за вас.', 'Drowned rise from the ground to fight for you.'),
    'ember': ('Маленький огненный снаряд.', 'A small fire projectile.'),
    'fire_nova_bomb': ('Огненная бомба, взрывающаяся широким кольцом пламени.', 'A fire bomb that bursts into a wide ring of flame.'),
    'firebolt': ('Быстрая огненная стрела.', 'A fast fire bolt.'),
    'firebomb': ('Взрывающийся огненный снаряд. На себя — взрывной рывок.', 'An exploding fire projectile. On yourself, an explosive dash.'),
    'flame_lance': ('Раскалённый луч пламени.', 'A searing beam of flame.'),
    'flash_freeze_lake': ('Мгновенно превращает воду и землю вокруг в скользкий лёд.', 'Instantly turns water and ground around into slippery ice.'),
    'floating_platform': ('Площадка 3×3 прямо под ногами, даже посреди прыжка или падения.', 'A 3×3 platform right under your feet, even mid-jump or mid-fall.'),
    'flooded_circuit': ('Огромная электрическая лужа. Осторожно: вы тоже можете в ней оказаться.', 'A huge electrified pool. Careful: you can end up in it too.'),
    'fork': ('Молния с руки, которая ветвится по толпе.', 'Lightning from your hand that forks through a crowd.'),
    'freezing_ray': ('Луч холода, который постепенно замораживает цель.', 'A ray of cold that gradually freezes its target.'),
    'frost_bolt': ('Ледяной снаряд.', 'A frost projectile.'),
    'frostflow': ('Ледяная вода: намочить и сразу заморозить.', 'Icy water: soak and freeze at once.'),
    'frostshock': ('Холод и электричество одновременно.', 'Cold and electricity together.'),
    'frozen_ground': ('Колючий иней: морозит, ранит и сковывает всех в зоне, но льда не оставляет.', 'Biting frost that chills, hurts and grips everyone in the zone, leaving no ice.'),
    'frozen_host': ('Зимогоры-лучники: их стрелы замедляют.', 'Stray archers whose arrows slow.'),
    'glacier_hammer': ('Ледяная глыба: сбивает с ног, замораживает и делает камень хрупким.', 'A block of glacier ice: knocks down, freezes and makes stone brittle.'),
    'grasping_dead': ('Руки мертвецов из земли держат врагов и иссушают их.', 'Dead hands rise from the ground to hold and wither enemies.'),
    'grave_call': ('Из земли встают зомби-слуги.', 'Zombie servants rise from the ground.'),
    'grave_chill': ('Могильный холод: замедление, заморозка и иссушение.', 'Grave cold: slows, freezes and withers.'),
    'greater_mend': ('Короткая, но сильнейшая лечебная зона.', 'A short but most powerful healing zone.'),
    'guardian_stone': ('Тотем-хранитель лечит самого раненого союзника рядом.', 'A guardian totem heals the most wounded ally nearby.'),
    'haste_surge': ('Долгое ускорение шага и копания, в конце лёгкий откат.', 'A long boost to speed and mining, with a mild recoil at the end.'),
    'heal': ('Лечит существо в прицеле.', 'Heals the creature you aim at.'),
    'healing_pool': ('Целебная лужа, в которой все намокают.', 'A healing pool that soaks everyone inside.'),
    'healing_spring': ('Источник-тотем лечит всех рядом и может вернуть павшего.', 'A spring totem heals everyone nearby and can bring back the fallen.'),
    'hellfire_skull': ('Горящий череп: огонь и иссушение.', 'A burning skull: fire and withering.'),
    'hibernating_roots': ('Корни опутывают цель и защищают её.', 'Roots wrap the target and protect it.'),
    'ice_ramp': ('Ледяной шип растёт от ваших ног туда, куда вы смотрите: вверх — столб, вперёд — таран, под углом — лестница.', 'An ice spike grows from your feet where you look: up for a pillar, forward for a ram, at an angle for a ramp.'),
    'ice_spear': ('Ледяное копьё пригвождает цель к месту.', 'An ice spear that pins the target in place.'),
    'ice_step': ('Один ледяной блок-ступенька там, куда вы смотрите.', 'A single ice step where you look.'),
    'inferno': ('Сильнейшая струя огня.', 'The most powerful stream of fire.'),
    'jet': ('Напор воды, отталкивающий врагов.', 'A water jet that pushes enemies away.'),
    'legion_of_the_dead': ('Целая армия нежити встаёт за вас.', 'A whole undead army rises to fight for you.'),
    'living_bridge': ('Мост из живых корней: по нему можно лазить, и он восстанавливается.', 'A bridge of living roots: climbable and self-repairing.'),
    'living_bulwark': ('Небольшая стена из корней для укрытия.', 'A small root wall for cover.'),
    'living_wall': ('Большая прочная стена из корней.', 'A large, sturdy root wall.'),
    'magma': ('Раскалённый камень.', 'A molten rock.'),
    'magma_boulder': ('Огромная магматическая глыба оставляет лужу жара и осколки.', 'A huge magma boulder that leaves a hot patch and shards.'),
    'mend': ('Слабое лечение снарядом.', 'A light healing projectile.'),
    'mend_burst': ('Вспышка лечения по группе и шанс вернуть павшего.', 'A healing burst for a group, with a chance to bring back the fallen.'),
    'molten_boulder': ('Горячий валун с осколками и лужей жара.', 'A hot boulder with shards and a hot patch.'),
    'moving_bastion': ('Каменный столб под целью поднимает её вверх.', 'A stone pillar rises under the target and lifts it up.'),
    'mud': ('Ком грязи: мочит и ненадолго вязнет.', 'A clump of mud that soaks and briefly roots.'),
    'overcharge': ('Резкий рывок сил: скорость IV и прыгучесть, затем откат током.', 'A sharp burst: Speed IV and jump boost, then an electric recoil.'),
    'pebble': ('Маленький камень.', 'A small stone.'),
    'permafrost': ('Мёрзлый камень: удар и холод.', 'Frozen stone: impact and cold.'),
    'phoenix_leap': ('Огненный рывок, оставляющий горящий след.', 'A fiery dash that leaves a burning trail.'),
    'plague_mist': ('Струя чумной воды.', 'A stream of plague water.'),
    'plasma': ('Огонь и молния в одном снаряде.', 'Fire and lightning in one projectile.'),
    'plasma_detonation': ('Плазменный взрыв, особенно опасный по мокрым целям.', 'A plasma blast, especially deadly against wet targets.'),
    'plasma_orb': ('Медленная нестабильная сфера. Молния рядом подрывает её раньше.', 'A slow, unstable orb. Nearby lightning sets it off early.'),
    'preservation_field': ('Поле, которое лечит, защищает и тушит огонь.', 'A field that heals, protects and puts out fire.'),
    'quicksilt': ('Ледяная трясина: обездвиживает и морозит.', 'An icy quagmire that roots and freezes.'),
    'raise_dead': ('Поднимает недавно павших существ как слуг.', 'Raises recently fallen creatures as servants.'),
    'regenerate': ('Долгая зона мягкого лечения.', 'A long-lasting zone of gentle healing.'),
    'regeneration_mist': ('Целебный туман, который мешает видеть.', 'A healing mist that obscures vision.'),
    'resurrection': ('Полностью возвращает павшего к жизни.', 'Fully brings the fallen back to life.'),
    'rock': ('Средний камень.', 'A medium rock.'),
    'root_ledge': ('Столб корней, по которому можно забраться наверх.', 'A column of roots you can climb up.'),
    'rootstone': ('Камень с корнями: удар, лечение и обездвиживание.', 'Stone and roots: impact, healing and rooting.'),
    'sanctuary': ('Святилище лечит, защищает и возвращает павших.', 'A sanctuary that heals, protects and revives the fallen.'),
    'shock_field': ('Электрическое поле, которое бьёт током всех внутри.', 'An electric field that shocks everyone inside.'),
    'stone_sculpture': ('Своя собственная фигура, вырезанная из куба земли 5×5×5.', 'Your own shape, cut out of a 5×5×5 block of earth.'),
    'skybridge': ('Каменный мост, который висит в воздухе и идёт за наклоном взгляда.', 'A stone bridge that floats in the air and follows your gaze.'),
    'soul': ('Магия души: лечит и ранит.', 'Soul magic that heals and harms.'),
    'soul_return': ('Возвращает павшего игрока или существо с половиной здоровья.', 'Brings back a fallen player or creature with half health.'),
    'soul_siphon': ('Вытягивает жизнь из цели в вас.', 'Drains life from the target into you.'),
    'soul_storm': ('Молния душ срывается с руки и скачет по врагам.', 'A lightning of souls leaps from your hand across enemies.'),
    'spark': ('Маленькая искра, перескакивающая между целями.', 'A small spark that jumps between targets.'),
    'splash': ('Брызги воды.', 'A splash of water.'),
    'spring': ('Целебная вода.', 'Healing water.'),
    'stasis': ('Холод и аркана замедляют цель.', 'Cold and arcane slow the target.'),
    'stasis_field': ('Поле, в котором замедляются существа и снаряды.', 'A field that slows creatures and projectiles.'),
    'static_stasis': ('Поле стазиса с разрядами молний.', 'A stasis field crackling with lightning.'),
    'steam': ('Облако горячего пара.', 'A cloud of hot steam.'),
    'steam_mine': ('Паровая мина, которая взрывается, когда рядом враг.', 'A steam mine that bursts when an enemy comes close.'),
    'stone_platform': ('Каменная площадка 3×3 на поверхности.', 'A 3×3 stone platform on a surface.'),
    'stone_stair': ('Каменная лестница от ваших ног вперёд и вверх.', 'A stone staircase from your feet forward and up.'),
    'storm_puddle': ('Электрическая лужа для контроля области.', 'An electrified puddle for area control.'),
    'stormwater': ('Мокрый разряд: вода усиливает молнию.', 'A wet shock: water amplifies lightning.'),
    'suspension': ('Сгусток арканной воды.', 'A blob of arcane water.'),
    'tesla_spire': ('Медная башня с катушкой: сама бьёт молниями по врагам рядом.', 'A copper tower with a coil that zaps nearby enemies on its own.'),
    'thermal_fracture': ('Резкий перепад температур раскалывает камень.', 'A sudden temperature swing cracks the stone.'),
    'thermal_shock': ('Жар и холод разом: огромный урон по замёрзшим.', 'Heat and cold at once: massive damage to frozen targets.'),
    'thunder_lance': ('Молния пронзает всех на линии и уходит дальше цепью.', 'Lightning pierces everyone in a line and chains onward.'),
    'thunder_nova': ('Электрический взрыв в точке прицела.', 'An electric blast at the aim point.'),
    'thunderflame': ('Горящий снаряд с электрическим взрывом.', 'A burning projectile with an electric blast.'),
    'thunderstone': ('Заряженный камень.', 'A charged stone.'),
    'tomb_collapse': ('Обрушивает склеп: ломает блоки, бьёт и иссушает.', 'Collapses a tomb: breaks blocks, strikes and withers.'),
    'torrent': ('Сильный поток воды.', 'A strong water torrent.'),
    'vital_spark': ('Биоэлектричество: лечит, но бьёт током.', 'Bioelectricity: heals, but shocks.'),
    'volatile_magma_core': ('Магматическое ядро взрывается осколками и оставляет лужу жара.', 'A magma core that bursts into shards and leaves a hot patch.'),
    'volcanic_vent': ('Земля извергает жар и подбрасывает врагов.', 'The ground erupts with heat and launches enemies.'),
    'water_cannon': ('Мощнейший водяной напор, сносит с ног.', 'The most powerful water blast, knocks enemies flat.'),
    'witchfire': ('Магическое пламя.', 'Magical flame.'),
    'wraith_swarm': ('Летающие вредины проходят сквозь стены к врагам.', 'Flying vexes pass through walls to reach enemies.'),
}

EL = {'fire': ('огнём', 'fire'), 'lightning': ('молнией', 'lightning'), 'earth': ('землёй', 'earth'), 'arcane': ('арканой', 'arcane'),
      'death': ('смертью', 'death'), 'thermal': ('термоударом', 'thermal'), 'water': ('водой', 'water'), 'cold': ('холодом', 'cold')}
MOB_EFFECTS = {'night_vision': ('ночное зрение', 'Night Vision'), 'water_breathing': ('подводное дыхание', 'Water Breathing'),
              'dolphins_grace': ('грация дельфина (быстрое плавание)', "Dolphin's Grace (fast swimming)"),
              'fire_resistance': ('огнестойкость (огонь и лава не ранят)', 'Fire Resistance (immune to fire and lava)'),
              'haste': ('спешка (быстрое копание)', 'Haste (faster mining)'),
              'jump_boost': ('прыгучесть', 'Jump Boost')}
SUPPORT_WORLD = {'place_light', 'light_path', 'light_ring', 'place_glowstone', 'reveal', 'extinguish'}
MOBS = {'zombie': ('зомби', 'zombie'), 'skeleton': ('скелет', 'skeleton'), 'creeper': ('крипер', 'creeper'), 'drowned': ('утопленник', 'drowned'),
        'stray': ('зимогор', 'stray'), 'husk': ('кадавр', 'husk'), 'vex': ('вредина', 'vex')}
ROMAN = ['', 'I', 'II', 'III', 'IV', 'V']


def num(x, ru):
    s = ('%.2f' % x).rstrip('0').rstrip('.')
    return s.replace('.', ',') if ru else s


def sec(ticks, ru):
    return f'{num(ticks / 20, ru)} {"с" if ru else "s"}'


def hearts(hp, ru):
    return f'{num(hp / 2, ru)} ❤'


def status(e, ru):
    s, t, k = e['status'], e.get('duration_ticks', 100), int(e.get('strength', 1))
    d = sec(t, ru)
    table = {
        'withering': (f'иссушение: −{hearts(.5 + .5 * k, ru)} в секунду, {d}', f'withering: −{hearts(.5 + .5 * k, ru)} per second for {d}'),
        'regenerating': (f'регенерация: +{hearts(max(1, k), ru)} каждые 2 с, {d}', f'regeneration: +{hearts(max(1, k), ru)} every 2 s for {d}'),
        'arcane_unstable': (f'арканная нестабильность: −{hearts(.5 * k, ru)} каждые 2 с, {d}', f'arcane instability: −{hearts(.5 * k, ru)} every 2 s for {d}'),
        'stone_shell': (f'каменная кожа (сопротивление {ROMAN[min(3, k)]}), {d}', f'stone skin (Resistance {ROMAN[min(3, k)]}) for {d}'),
        'chilled': (f'замедление {ROMAN[k]}, {d}', f'Slowness {ROMAN[k]} for {d}'),
        'rooted': (f'обездвиживание, {d}', f'rooted in place for {d}'),
        'obscured': (f'слепота, {d}', f'blindness for {d}'),
        'brittle': (f'хрупкость (урон землёй ×2), {d}', f'brittle (earth damage ×2) for {d}'),
        'haste': (f'скорость {ROMAN[min(4, k) + 1]}, {d}', f'Speed {ROMAN[min(4, k) + 1]} for {d}'),
        'levitating': (f'левитация, {d}', f'levitation for {d}'),
        'slow_falling': (f'плавное падение, {d}', f'slow falling for {d}'),
    }
    return table[s][0 if ru else 1]


def mob_list(nodes, ru):
    return ', '.join(f'{MOBS[n["entity"].split(":")[1]][0 if ru else 1]} ×{n["count"]}' for n in nodes)


HANDWRITTEN = {'curse', 'flare', 'hex', 'magnetic_lash', 'overgrowth', 'shatter_bolt', 'sheep', 'sheepocalypse',
               'soul_chain', 'thorn_spike'}
HARMFUL = {'deal_damage', 'drain_life', 'modify_temperature', 'add_charge', 'apply_force', 'chain_targets', 'destroy_blocks', 'heave_blocks', 'brittle_terrain', 'conditional'}


def effects(graph, spell, ru, pulse=False, friendly=False):
    if friendly:
        graph = [e for e in graph if e['type'] not in HARMFUL and e.get('status') not in ('withering', 'arcane_unstable', 'chilled', 'rooted', 'obscured', 'brittle', 'levitating')]
    lines = []
    total = {}
    for e in graph:
        if e['type'] == 'deal_damage':
            total[e.get('element', 'arcane')] = total.get(e.get('element', 'arcane'), 0) + e['amount']
    if total:
        parts = [f'{hearts(v, ru)} {EL[k][0 if ru else 1]}' for k, v in total.items()]
        note = []
        if 'lightning' in total:
            note.append('молния по мокрым ×2' if ru else 'lightning ×2 on wet')
        if 'fire' in total:
            note.append('огонь по мокрым ×0,5' if ru else 'fire ×0.5 on wet')
        if 'earth' in total:
            note.append('земля по замёрзшим ×2' if ru else 'earth ×2 on frozen')
        lines.append(('урон ' if ru else 'damage ') + ' + '.join(parts) + (f' ({", ".join(note)})' if note else ''))
    summons = [e for e in graph if e['type'] == 'summon_undead']
    if summons and not pulse:
        t = sec(summons[0]['duration_ticks'], ru)
        lines.append(f'призывает слуг на {t}: {mob_list(summons, ru)}' if ru else f'summons servants for {t}: {mob_list(summons, ru)}')
    for e in graph:
        k = e['type']
        if k in ('deal_damage', 'summon_undead', 'create_construction'):
            continue
        a = e.get('amount', 0)
        if k == 'heal':
            lines.append(f'лечение +{hearts(a, ru)}' if ru else f'heals {hearts(a, ru)}')
        elif k == 'drain_life':
            f = e.get('fraction', .5)
            lines.append(f'похищение жизни: {hearts(a, ru)} урона, вы лечитесь на {hearts(a * f, ru)}' if ru else f'life drain: {hearts(a, ru)} damage, you heal {hearts(a * f, ru)}')
        elif k == 'set_wet':
            t = sec(e['duration_ticks'], ru)
            puddle = '' if pulse or spell['delivery'].get('object_kind') == 'bubble' else (' и лужа на земле' if ru else ' and leaves a puddle')
            lines.append(f'намокание на {t}{puddle}' if ru else f'soaked for {t}{puddle}')
        elif k == 'modify_temperature':
            if a > 0:
                lines.append(f'жар: поджигает на {sec(a * 3, ru)} (мокрых только высушивает), размораживает; плавит лёд, жжёт корни' if ru
                             else f'heat: sets on fire for {sec(a * 3, ru)} (wet targets just dry off), thaws; melts ice and burns roots')
            else:
                lines.append(f'холод {num(-a, ru)}: тушит и замедляет, при 100 накопленного холода — заморозка на 4 с (мокрым ×2,5); лужи замерзают' if ru
                             else f'cold {num(-a, ru)}: extinguishes and slows, freezes for 4 s at 100 built-up cold (×2.5 on wet); puddles freeze')
        elif k == 'add_charge':
            lines.append(f'заряд {num(a, ru)}: слабость, при 65 заряда — оглушение на 1 с (мокрым ×2)' if ru
                         else f'charge {num(a, ru)}: weakness, stuns for 1 s at 65 charge (×2 on wet)')
        elif k == 'apply_status':
            if not (e['status'] == 'levitating' and spell['delivery'].get('object_kind') == 'bubble'):
                lines.append(status(e, ru))
        elif k == 'apply_effect':
            name = MOB_EFFECTS[e['effect'].split(':')[1]][0 if ru else 1]
            level = int(e.get('strength', 1))
            name = name.replace(' (', f' {ROMAN[level]} (', 1) if level > 1 and ' (' in name else name + (f' {ROMAN[level]}' if level > 1 else '')
            lines.append(f'{name}, {sec(e["duration_ticks"], ru)}')
        elif k == 'place_light':
            where = ('над головой' if ru else 'above your head') if e.get('offset_y') else ('в месте попадания' if ru else 'where it lands')
            lines.append(f'светящийся огонёк {where}: свет 15, висит в воздухе, остаётся навсегда, снимается ударом' if ru
                         else f'a glowing wisp {where}: light level 15, floats in the air, permanent, removed with a hit')
        elif k == 'light_path':
            lines.append(f'цепочка огоньков от вас до точки прицела через каждые {num(e["spacing"], ru)} бл., до {e["count"]} шт.; свет 15, остаются навсегда' if ru
                         else f'a chain of wisps from you to the aim point every {num(e["spacing"], ru)} blocks, up to {e["count"]}; light level 15, permanent')
        elif k == 'light_ring':
            lines.append(f'{e["count"]} огоньков по кругу радиусом {num(e["radius"], ru)} бл. вокруг вас; свет 15, остаются навсегда' if ru
                         else f'{e["count"]} wisps in a {num(e["radius"], ru)}-block circle around you; light level 15, permanent')
        elif k == 'place_glowstone':
            where = ('над головой' if ru else 'above your head') if e.get('offset_y') else ('в точке прицела' if ru else 'at the aim point')
            lines.append(f'блок магического светокамня {where}: свет 15, держится без опоры, остаётся навсегда, при разрушении ничего не выпадает' if ru
                         else f'a magic glowstone block {where}: light level 15, needs no support, permanent, drops nothing when broken')
        elif k == 'reveal':
            lines.append(f'подсвечивает всех, кому вы можете навредить, в радиусе {num(e["radius"], ru)} бл.: их видно сквозь стены {sec(e["duration_ticks"], ru)}' if ru
                         else f'highlights everyone you can harm within {num(e["radius"], ru)} blocks, visible through walls for {sec(e["duration_ticks"], ru)}')
        elif k == 'extinguish':
            lines.append(f'тушит огонь в радиусе {num(e["radius"], ru)} бл.: горящие блоки и всех существ, включая вас' if ru
                         else f'puts out fire within {num(e["radius"], ru)} blocks: burning blocks and every creature, you included')
        elif k == 'remove_status':
            lines.append({'burning': ('тушит огонь', 'puts out fire'), 'withering': ('снимает иссушение', 'removes withering')}[e['status']][0 if ru else 1])
        elif k == 'apply_force':
            if e.get('lift', 0) >= .5:
                lines.append('подбрасывает вверх' if ru else 'launches upward')
            elif a >= 1:
                lines.append('сильно отбрасывает' if ru else 'strong knockback')
            elif a >= .45:
                lines.append('отбрасывает' if ru else 'knockback')
            else:
                lines.append('лёгкий толчок' if ru else 'light push')
        elif k == 'chain_targets':
            lines.append(f'затем молния перескакивает дальше: до {e["count"]} целей подряд, прыжок до {num(e["range"], ru)} бл., каждой {hearts(e["amount"], ru)} и заряд 40 (мокрым урон ×2 и прыжок дальше, в лужах дальность ×1,5)' if ru
                         else f'then the lightning jumps onward: up to {e["count"]} targets in a row, jumps up to {num(e["range"], ru)} blocks, {hearts(e["amount"], ru)} and 40 charge each (×2 damage and longer jumps on wet, ×1.5 range in puddles)')
        elif k == 'grow_plants':
            lines.append(f'рост: до {e["budget"]} блоков рядом — посевы +2 стадии, земля зарастает травой, корни тянутся вверх' if ru
                         else f'growth: up to {e["budget"]} nearby blocks — crops +2 stages, dirt turns to grass, roots grow upward')
        elif k == 'destroy_blocks':
            lines.append(f'ломает до {e["budget"]} блоков в радиусе {num(min(3, e["radius"]), ru)} бл. (хрупких — вдвое больше)' if ru
                         else f'breaks up to {e["budget"]} blocks within {num(min(3, e["radius"]), ru)} blocks (twice as many if brittle)')
        elif k == 'heave_blocks':
            lines.append(f'вздыбливает землю: до {e["budget"]} блоков в радиусе {num(e["radius"], ru)} бл. взлетают вверх, бьют на лету и падают обратно, заваливая всё под собой' if ru
                         else f'heaves the ground: up to {e["budget"]} blocks within {num(e["radius"], ru)} blocks fly up, hurt on the way and crash back down, burying whatever stands below')
        elif k == 'brittle_terrain':
            lines.append(f'блоки в радиусе {num(min(3, e["radius"]), ru)} бл. становятся хрупкими на 8 с' if ru
                         else f'blocks within {num(min(3, e["radius"]), ru)} blocks turn brittle for 8 s')
        elif k == 'revive':
            r = num(spell['delivery'].get('radius', 3), ru)
            p = round(e['health_fraction'] * 100)
            lines.append(f'воскрешает ближайшего павшего игрока или существо в радиусе {r} бл. с {p}% здоровья' if ru
                         else f'revives the nearest fallen player or creature within {r} blocks with {p}% health')
        elif k == 'raise_dead':
            lines.append(f'поднимает до {e["count"]} недавно погибших существ в радиусе {num(e["radius"], ru)} бл. слугами на {sec(e["duration_ticks"], ru)}' if ru
                         else f'raises up to {e["count"]} recently slain creatures within {num(e["radius"], ru)} blocks as servants for {sec(e["duration_ticks"], ru)}')
        elif k == 'conditional':
            inner = '; '.join(effects(e['effects'], spell, ru, pulse))
            lines.append(f'если цель заморожена или хрупкая: {inner}' if ru else f'if the target is frozen or brittle: {inner}')
        elif k == 'delay':
            inner = '; '.join(effects(e['effects'], spell, ru, pulse))
            lines.append(f'через {sec(e["ticks"], ru)}: {inner}' if ru else f'{sec(e["ticks"], ru)} later: {inner}')
        elif k == 'spawn_zone':
            if not pulse:
                lines.append(f'оставляет зону радиусом {num(e["radius"], ru)} бл. на {sec(e["duration_ticks"], ru)}, которая раз в секунду повторяет эти эффекты' if ru
                             else f'leaves a {num(e["radius"], ru)}-block zone for {sec(e["duration_ticks"], ru)} that repeats these effects every second')
        else:
            raise SystemExit('unknown effect ' + k)
    return list(dict.fromkeys(lines))


MATERIAL = {
    'earth': ('магическая земля, остаётся навсегда', 'magic earth, permanent'),
    'copper': ('магическая медь, исчезает через 30 с', 'magic copper, fades after 30 s'),
    'root': ('корни: по ним можно лазить, огонь их сжигает, остаются навсегда', 'roots: climbable, burn in fire, permanent'),
    'ice': ('лёд, тает через 30 с', 'ice, melts after 30 s'),
    'arcane': ('арканный кристалл, исчезает через 20 с', 'arcane crystal, fades after 20 s'),
}


def construction(spell, ru, self=False):
    c, n = spell['construction'], spell['construction']['budget']
    shape = 'anchor' if spell['delivery'].get('object_kind') == 'anchor' else spell['shape']
    if spell['origin'] == 'under_target' and not self:
        shape = 'lift'
    at = ('у ваших ног', 'at your feet') if self else (('в точке прицела', 'at the aim point'))
    W = c['width']
    if c.get('under_caster'):
        shape = 'under'
    if c.get('template'):
        shape = 'template_' + c['template']
    texts = {
        'under': (f'площадка {W}×{max(1, n // W)} прямо под вашими ногами, даже в воздухе', f'a {W}×{max(1, n // W)} platform right under your feet, even in mid-air'),
        'platform': (f'площадка {W}×{max(1, n // W)} {at[0]}', f'a {W}×{max(1, n // W)} platform {at[1]}') if W > 1 else
                    (f'столб из {n} бл. {at[0]}, поднимает стоящего на нём', f'a {n}-block pillar {at[1]} that lifts whoever stands on it') if n > 1 else
                    (f'один блок-ступенька {at[0]}', f'a single step block {at[1]}'),
        'lift': (f'под существом в прицеле растёт столб из {n} бл. и поднимает его', f'a {n}-block pillar grows under the creature you aim at and lifts it'),
        'pillar': (f'медная башня из {n - 1} бл. с катушкой Теслы наверху {at[0]}', f'a copper tower of {n - 1} blocks topped with a Tesla coil {at[1]}') if spell['delivery'].get('tesla') else
                  (f'столб из {n} бл. {at[0]}', f'a {n}-block pillar {at[1]}'),
        'wall': (f'стена высотой {c["height"]} из {n} бл. {at[0]}, тянется вправо поперёк взгляда', f'a wall {c["height"]} high of {n} blocks {at[1]}, extending to the right across your view'),
        'bridge': (f'мост из {n} бл. от точки прицела вперёд; поднимите или опустите взгляд — мост пойдёт вверх или вниз', f'a {n}-block bridge forward from the aim point; look up or down to slope it'),
        'stair': (f'лестница из {n} бл. от ваших ног вперёд и вверх', f'a {n}-block staircase from your feet forward and up'),
        'spike': (f'шип до {n} бл. от ваших ног по направлению взгляда (вверх — столб, вперёд — таран, наискось — лестница), со случайным наклоном и боковыми отростками, останавливается о препятствие', f'a spike of up to {n} blocks from your feet along your view (up for a pillar, forward for a ram, diagonal for a ramp), with a random tilt and side shards, stopping at obstacles') if self else
                  (f'шип до {n} бл. вырастает из поверхности в точке прицела со случайным наклоном и боковыми отростками, останавливается о препятствие; если прицел в небо — растёт от ваших ног по направлению взгляда', f'a spike of up to {n} blocks bursts out of the surface at the aim point with a random tilt and side shards, stopping at obstacles; aimed at the sky, it grows from your feet along your view'),
        'growth': (f'клетка из {n} бл. вокруг {"вас" if self else "цели"}', f'a {n}-block cage around {"you" if self else "the target"}'),
        'anchor': (f'парящий столб из {n} бл. {at[0]}', f'a floating {n}-block column {at[1]}'),
        'template_wall': ('стена 5×3 в точке прицела поперёк взгляда под любым углом, каждый столбец встаёт на землю. Если смотреть вверх — над вами встаёт двускатная крыша-шатёр шириной 9 и длиной 5 (концы открыты)',
                          'a 5×3 wall at the aim point, across your view at any angle, each column settling on the ground. Look up to raise a gabled tent roof over you instead, 9 wide and 5 long with open ends'),
        'template_slope': ('стена 5 в ширину под углом 45°: встаёт основанием в точке прицела и поднимается в вашу сторону навесом, закрывая и спереди, и с неба; поворачивается под любым углом, скат сплошной, без щелей',
                           'a 5-wide wall at 45°: its base stands at the aim point and it rises toward you like an awning, covering you from the front and from the sky; it turns to any angle and the slope is solid with no gaps'),
        'template_slab': ('плита 5×5 по направлению взгляда: вверх — крыша над головой (3 блока над ногами), вниз — пол прямо под вами, прямо — настил вперёд на 5 блоков на уровне пола. Может висеть в воздухе, занятые блоки пропускает',
                          'a 5×5 slab that follows your view: look up for a roof over your head (3 blocks above your feet), down for a floor right under you, straight ahead for a 5-block deck forward at floor level. It can hang in mid-air and skips occupied blocks'),
        'template_redoubt': ('полукольцо стены высотой 2 радиусом 3, обращённое к точке прицела: вершина дуги встаёт там, куда вы целитесь, каждый столбец встаёт на землю',
                             'a 2-high half ring of wall with a 3-block radius facing the aim point: the top of the arc stands where you aim, each column settling on the ground'),
        'template_custom': ('фигура, которую вы сами нарисовали в кубе 5×5×5 (редактор формы открывается на K): ставится в точке прицела и поворачивается вслед за взглядом',
                           'the shape you drew yourself in a 5×5×5 cube (press K for the shape editor): it is built at the aim point and turns to follow your view'),
    }
    text = texts[shape][0 if ru else 1]
    extra = []
    if c.get('floating') and shape not in ('anchor', 'under'):
        extra.append('может висеть в воздухе без опоры' if ru else 'can hang in mid-air without support')
    if c.get('regenerates'):
        extra.append('повреждённые блоки отрастают' if ru else 'damaged blocks regrow')
    mat = MATERIAL[c['material']][0 if ru else 1]
    return text + (f'; {"; ".join(extra)}' if extra else '') + (f'. Материал — {mat}' if ru else f'. Material: {mat}')


def cap(s):
    return s[:1].upper() + s[1:]


def block(head, lines, empty_ru='', empty_en=''):
    if not lines:
        return head
    return head + '\n' + '\n'.join('• ' + cap(l) for l in lines)


def is_construction(spell):
    return spell['shape'] in ('platform', 'pillar', 'wall', 'bridge', 'stair', 'spike', 'growth') or spell['delivery'].get('object_kind') == 'anchor'


def zone_who(spell, ru, self):
    if self:
        return 'на всех внутри, включая вас' if ru else 'on everyone inside, including you'
    if spell['formula'].get('life'):
        return 'на всех внутри, включая вас (урон и контроль — только по врагам)' if ru else 'on everyone inside, including you (damage and control only hit enemies)'
    return 'на всех внутри, кроме вас' if ru else 'on everyone inside except you'


def primary(spell, ru):
    d, shape, eff = spell['delivery'], spell['shape'], spell['effects']
    R = lambda key='radius': num(d.get(key, 3), ru)
    T = lambda key='duration_ticks', default=160: sec(d.get(key, default), ru)
    if is_construction(spell):
        head = cap(construction(spell, ru)) + '.'
        if d.get('tesla'):
            return block(head + (f' Пока башня стоит (до {T()}), катушка раз в {T("interval_ticks", 20)} бьёт молнией до {d.get("targets", 3)} ближайших врагов в радиусе {R()} бл., которых видит:' if ru
                                 else f' While the tower stands (up to {T()}), the coil zaps up to {d.get("targets", 3)} nearest visible enemies within {R()} blocks every {T("interval_ticks", 20)}:'), effects(eff, spell, ru))
        if d.get('conductive'):
            return block(head + (' У основания на 10 с появляется электрическое поле радиусом 4 бл., раз в секунду:' if ru
                                 else ' An electric field with a 4-block radius appears at its base for 10 s, every second:'), effects(eff, spell, ru, True))
        if shape == 'growth':
            return block(head + (' Каждый выросший блок действует на существ рядом с центром:' if ru else ' Each block that grows affects creatures near the center:'), effects(eff, spell, ru, True))
        return head
    if d.get('meteor'):
        return block(f'Через {T("delay_ticks", 12)} над точкой прицела появляется метеор, падает с высоты 10 бл. и ещё через {T("delay_ticks", 12)} взрывается радиусом {R("burst_radius")} бл.:' if ru
                     else f'After {T("delay_ticks", 12)} a meteor appears above the aim point, falls from 10 blocks up and {T("delay_ticks", 12)} after landing explodes in a {R("burst_radius")}-block radius:', effects(eff, spell, ru))
    if shape in ('projectile', 'burst_projectile'):
        arc = ('по дуге ' if ru else 'in an arc ') if d['gravity'] > 0 else ''
        burst = shape == 'burst_projectile' or d.get('shards') or d.get('volatile')
        if d.get('volatile'):
            head = (f'Медленная сфера летит {arc}по взгляду и взрывается радиусом {R("burst_radius")} бл. при касании, через 4 с полёта или от цепной молнии рядом.' if ru
                    else f'A slow orb flies {arc}where you look and explodes in a {R("burst_radius")}-block radius on contact, after 4 s of flight, or when chain lightning passes nearby.')
        elif burst:
            head = (f'Снаряд летит {arc}по взгляду и при попадании взрывается радиусом {R("burst_radius")} бл.' if ru
                    else f'A projectile flies {arc}where you look and explodes in a {R("burst_radius")}-block radius on hit.')
        elif all(e['type'] in SUPPORT_WORLD for e in eff):
            head = f'Огонёк летит {arc}по взгляду до первого блока или существа.' if ru else f'A wisp flies {arc}where you look until it touches a block or creature.'
        else:
            head = f'Снаряд летит {arc}по взгляду и поражает первую цель.' if ru else f'A projectile flies {arc}where you look and strikes the first target.'
        if d.get('delay_ticks'):
            head += f' Взрыв происходит через {T("delay_ticks")} после попадания.' if ru else f' The explosion comes {T("delay_ticks")} after impact.'
        if d.get('shards'):
            head += f' Вокруг разлетаются {d["shards"]} осколка с теми же эффектами по ближайшим существам.' if ru else f' {d["shards"]} shards scatter around with the same effects on creatures they touch.'
        return block(head + (' Эффекты:' if ru else ' Effects:'), effects(eff, spell, ru))
    if shape == 'beam':
        held = spell.get('cast_mode') != 'instant'
        if d.get('end_burst'):
            head = (f'Мгновенный луч до точки прицела бьёт всех на линии и взрывается в конце радиусом {R("end_burst")} бл.' if ru
                    else f'An instant beam to the aim point hits everyone along it and explodes at the end in a {R("end_burst")}-block radius.')
        else:
            head = 'Луч до точки прицела бьёт всех на линии.' if ru else 'A beam to the aim point hits everyone along it.'
        if held:
            head += ' В мгновенном режиме бьёт непрерывно, пока держите.' if ru else ' In instant mode it keeps firing while held.'
        return block(head + (' Эффекты:' if ru else ' Effects:'), effects(eff, spell, ru))
    if shape == 'spray':
        return block('Расширяющаяся струя (до 3 бл. в ширину) бьёт всех перед вами, в мгновенном режиме — непрерывно, пока держите:' if ru
                     else 'A widening stream (up to 3 blocks wide) hits everyone in front of you; in instant mode it keeps going while held:', effects(eff, spell, ru))
    if shape == 'chain':
        if d.get('pierce'):
            head = 'Молния бьёт с руки по прямой до точки прицела и поражает всех на линии, затем уходит цепью от последнего.' if ru else 'Lightning shoots from your hand straight to the aim point, striking everyone along the line, then chains on from the last one.'
        else:
            head = 'Молния бьёт с руки в существо в прицеле (или первое на линии взгляда).' if ru else 'Lightning shoots from your hand into the creature you aim at (or the first one in your line of sight).'
        if sum(spell['formula'].values()) >= 4:
            head += ' В мгновенном режиме бьёт дважды в секунду, пока держите.' if ru else ' In instant mode it strikes twice a second while held.'
        return block(head + (' Эффекты:' if ru else ' Effects:'), effects(eff, spell, ru))
    if shape == 'targeted_explosion':
        return block(f'Взрыв в точке прицела радиусом {R()} бл. (вас не задевает):' if ru else f'An explosion at the aim point with a {R()}-block radius (does not hit you):', effects(eff, spell, ru))
    if shape == 'targeted_zone':
        extra = []
        if d.get('ice_surface'):
            extra.append('вода и поверхность под ней покрываются льдом' if ru else 'water and ground beneath turn to ice')
        if d.get('slow_projectiles'):
            extra.append('снаряды внутри замедляются' if ru else 'projectiles inside slow down')
        head = (f'Зона в точке прицела радиусом {R()} бл. на {T()}' if ru else f'A zone at the aim point with a {R()}-block radius for {T()}')
        head += (f' ({"; ".join(extra)})' if extra else '') + (f'. Раз в секунду {zone_who(spell, ru, False)}:' if ru else f'. Every second, {zone_who(spell, ru, False)}:')
        return block(head, effects(eff, spell, ru, True))
    if shape == 'direct_target' and all(e['type'] in SUPPORT_WORLD for e in eff):
        return block('Там, куда вы смотрите:' if ru else 'Where you look:', effects(eff, spell, ru))
    if shape == 'direct_target' and all(e['type'] in ('apply_effect', 'remove_status') for e in eff):
        return block('Действует на существо в прицеле, в том числе на союзника (на себя — через Self):' if ru else 'Affects the creature you aim at, allies included (use Self for yourself):', effects(eff, spell, ru))
    if shape == 'direct_target':
        return block('Действует на существо в прицеле:' if ru else 'Affects the creature you aim at:', effects(eff, spell, ru))
    if shape == 'mine':
        return block(f'Мина в точке прицела на {T()}: взводится за 1 с и взрывается радиусом {R()} бл., когда рядом враг:' if ru
                     else f'A mine at the aim point for {T()}: arms in 1 s and explodes in a {R()}-block radius when an enemy comes near:', effects(eff, spell, ru))
    if shape == 'dash':
        return dash_text(spell, ru)
    kind = d.get('object_kind')
    if kind == 'undead':
        only = all(e['type'] == 'summon_undead' for e in eff)
        return block(('В точке прицела:' if ru else 'At the aim point:') if only else (f'В точке прицела (эффекты — по всем в радиусе {R()} бл.):' if ru else f'At the aim point (effects hit everyone within {R()} blocks):'), effects(eff, spell, ru))
    if kind == 'bubble':
        lift = T('lift_ticks', 100)
        if d.get('area'):
            return block(f'Все в радиусе {R()} бл. от точки прицела (кроме вас) попадают в пузыри и взлетают (левитация {lift}):' if ru
                         else f'Everyone within {R()} blocks of the aim point (except you) is caught in bubbles and floats up (levitation for {lift}):', effects(eff, spell, ru))
        return block(f'Существо в прицеле попадает в пузырь и взлетает (левитация {lift}):' if ru else f'The creature you aim at is caught in a bubble and floats up (levitation for {lift}):', effects(eff, spell, ru))
    who = ('самого раненого союзника' if ru else 'the most wounded ally') if d.get('heal_weakest') else ('всех, включая вас' if ru else 'everyone, including you')
    return block(f'Ставит тотем ({num(d.get("object_health", 12) / 2, ru)} ❤) в точке прицела на {T()}. Раз в секунду в радиусе {R()} бл. на {who}:' if ru
                 else f'Places a totem ({num(d.get("object_health", 12) / 2, ru)} ❤) at the aim point for {T()}. Every second within {R()} blocks, on {who}:', effects(eff, spell, ru, True, bool(d.get('heal_weakest'))))


def self_block(spell, ru, head):
    lines = effects([e for e in spell['self']['effects'] if e['type'] != 'create_construction'], spell, ru)
    return block(head, lines) if lines else head.rsplit('. ', 1)[0].rstrip(':') + ('. Дополнительных эффектов нет.' if ru else '. No extra effects.')


def self_text(spell, ru):
    mode, d = spell['self']['mode'], spell['delivery']
    eff = spell['self']['effects']
    R = num(d.get('radius', 3), ru)
    if mode == 'nova':
        return self_block(spell, ru, f'Взрыв вокруг вас радиусом {R} бл., задевает и вас:' if ru else f'An explosion around you with a {R}-block radius, hitting you as well:')
    if mode == 'zone':
        return block(f'Зона вокруг вас радиусом {R} бл. на {sec(d.get("duration_ticks", 160), ru)}. Раз в секунду {zone_who(spell, ru, True)}:' if ru
                     else f'A zone around you with a {R}-block radius for {sec(d.get("duration_ticks", 160), ru)}. Every second, {zone_who(spell, ru, True)}:', effects(eff, spell, ru, True))
    if mode == 'construct':
        head = cap(construction(spell, ru, True)) + '.'
        if d.get('tesla'):
            return block(head + (f' Башня поднимает вас наверх; катушка раз в {sec(d.get("interval_ticks", 20), ru)} бьёт молнией до {d.get("targets", 3)} врагов в радиусе {R} бл.:' if ru
                                 else f' The tower lifts you up; every {sec(d.get("interval_ticks", 20), ru)} the coil zaps up to {d.get("targets", 3)} enemies within {R} blocks:'), effects(spell['effects'], spell, ru))
        if spell['shape'] == 'growth':
            return block(head + (' Каждый выросший блок действует на вас и всех рядом:' if ru else ' Each block that grows affects you and everyone nearby:'), effects(eff, spell, ru, True))
        return self_block(spell, ru, head + (' На вас:' if ru else ' On you:'))
    if mode == 'summon':
        kind = d.get('object_kind')
        if kind == 'undead':
            only = all(e['type'] == 'summon_undead' for e in eff)
            return self_block(spell, ru, ('Рядом с вами:' if ru else 'Next to you:') if only else (f'Рядом с вами (эффекты — по всем в радиусе {R} бл., включая вас):' if ru else f'Next to you (effects hit everyone within {R} blocks, including you):'))
        if kind == 'bubble':
            lift = sec(d.get('lift_ticks', 100), ru)
            if d.get('area'):
                return self_block(spell, ru, f'Все в радиусе {R} бл. от вас, включая вас, попадают в пузыри и взлетают (левитация {lift}):' if ru else f'Everyone within {R} blocks of you, you included, is caught in bubbles and floats up (levitation for {lift}):')
            return self_block(spell, ru, f'Вы сами попадаете в пузырь и взлетаете (левитация {lift}):' if ru else f'You are caught in the bubble yourself and float up (levitation for {lift}):')
        who = ('на самого раненого союзника' if ru else 'on the most wounded ally') if d.get('heal_weakest') else ('на всех, включая вас' if ru else 'on everyone, including you')
        return block(f'Тотем ставится у ваших ног. Раз в секунду в радиусе {R} бл. {who}:' if ru else f'The totem appears at your feet. Every second within {R} blocks, {who}:', effects(eff, spell, ru, True, bool(d.get('heal_weakest'))))
    if mode == 'dash':
        return dash_text(spell, ru)
    if mode == 'meteor':
        return self_block(spell, ru, 'Метеор падает прямо на вас:' if ru else 'The meteor falls right on you:')
    if eff and all(e['type'] in SUPPORT_WORLD for e in eff):
        return self_block(spell, ru, 'Вокруг вас:' if ru else 'Around you:')
    return self_block(spell, ru, 'На вас:' if ru else 'On you:')


def dash_text(spell, ru):
    trail = block(f'Полсекунды за вами остаётся след из зон радиусом 1 бл. на 2 с; раз в секунду {zone_who(spell, ru, False)}:' if ru
                  else f'For half a second you leave a trail of 1-block zones lasting 2 s; every second, {zone_who(spell, ru, False)}:', effects(spell['effects'], spell, ru, True))
    return self_block(spell, ru, 'Рывок по взгляду. На вас:' if ru else 'Dash where you look. On you:') + '\n' + trail


ELEMENT_RU = {'Fire': 'Огонь', 'Water': 'Вода', 'Cold': 'Холод', 'Lightning': 'Молния',
              'Earth': 'Земля', 'Life': 'Жизнь', 'Arcane': 'Аркан', 'Death': 'Смерть'}
SUFFIX = re.compile(r'^(.*) · (%s)( ×\d)?$' % '|'.join(ELEMENT_RU))


def localized_name(name):
    death = ''
    while True:
        m = SUFFIX.match(name)
        if not m:
            break
        name, death = m[1], ' · ' + ELEMENT_RU[m[2]] + (m[3] or '') + death
    if name in NAMES:
        return NAMES[name] + death
    m = re.fullmatch(r'Convergence ((?:\w+ / )*\w+)', name)
    if m:
        return 'Слияние ' + ' / '.join(SHORT_RU[p] for p in m[1].split(' / ')) + death
    convergence = name.endswith(' Convergence')
    core_name = name[:-len(' Convergence')] if convergence else name
    words = []
    while core_name not in CORES:
        first, core_name = core_name.split(' ', 1)
        words.append(first)
    words.sort(key=lambda w: w != 'Greater')
    noun, gender = CORES[core_name]
    index = 'mfn'.index(gender)
    result = noun
    if words:
        result = noun[0].lower() + noun[1:]
        result = ' '.join(ADJECTIVES[w][index] for w in words) + ' ' + result
        result = result[0] + result[1:].lower() if False else result
        parts = result.split(' ')
        result = ' '.join([parts[0]] + [p.lower() for p in parts[1:]])
    return result + (' · слияние' if convergence else '') + death


def main():
    ru_lang = json.loads((LANG / 'ru_ru.json').read_text(encoding='utf-8'))
    en_lang = json.loads((LANG / 'en_us.json').read_text(encoding='utf-8'))
    for lang in (ru_lang, en_lang):
        for key in [k for k in lang if k.startswith('spell.magicka.')]:
            if key.split('.')[2] not in HANDWRITTEN:
                del lang[key]
    count = 0
    for path in sorted(SPELLS.glob('*.json')):
        spell = json.loads(path.read_text(encoding='utf-8'))
        key = 'spell.magicka.' + spell['id'].split(':')[1]
        if spell['id'].split(':')[1] in HANDWRITTEN:
            continue
        flavor = FLAVOR.get(spell['id'].split(':')[1]) if spell['tier'] == 'Signature' else None
        if spell['tier'] == 'Signature' and not flavor:
            raise SystemExit('missing flavor ' + spell['id'])
        texts = {}
        for ru in (True, False):
            body = primary(spell, ru)
            if flavor:
                body = flavor[0 if ru else 1] + '\n' + body
            texts[ru] = (localized_name(spell['name']) if ru else spell['name'], body, self_text(spell, ru))
        for lang, ru in ((ru_lang, True), (en_lang, False)):
            lang[key], lang[key + '.desc'], lang[key + '.self'] = (t.replace('%', '%%') for t in texts[ru])
        spell['primary'], spell['self_description'] = texts[False][1], texts[False][2]
        path.write_text(json.dumps(spell, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        count += 1
    for name, lang in (('ru_ru.json', ru_lang), ('en_us.json', en_lang)):
        (LANG / name).write_text(json.dumps(lang, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Localized {count} spells')


if __name__ == '__main__':
    main()
