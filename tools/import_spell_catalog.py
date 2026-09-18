"""Reproducibly import every row of the authored Markdown catalog into datapack JSON.

Run with Python 3.11+. Text is always read/written as UTF-8; numeric balance below is
prototype tuning, while formula/name/tier/shape/origin/Primary/Self come from the MD.
"""
from pathlib import Path
import collections
import itertools
import json
import re
import sys
from support_spells import SUPPORT
from build_spells import BY_FORMULA as BUILD
import spell_design

ROOT = Path(__file__).resolve().parents[1]
ELEMENTS = ['fire', 'water', 'cold', 'lightning', 'earth', 'life', 'arcane']
CHECK = '--check' in sys.argv

def output(path, data):
    text=json.dumps(data,ensure_ascii=False,indent=2)+'\n'
    if CHECK:
        texts=lambda d:{k:v for k,v in d.items() if k not in ('primary','self_description')} if isinstance(d,dict) else d[:len(data)]
        assert path.exists() and texts(json.loads(path.read_text(encoding='utf-8')))==texts(data), f'Catalog differs: {path}'
    else:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8')

def node(kind, **kwargs):
    return dict(type=kind, **kwargs)

def status(name, ticks, strength=1):
    return node('apply_status', status=name, duration_ticks=ticks, strength=strength)

def generic(f, self_cast=False):
    effects = []
    fire, water, cold, bolt, earth, life, arcane = (f.get(e, 0) for e in ELEMENTS)
    if water:
        effects += [node('set_wet', duration_ticks=100+water*60), node('apply_force', amount=water*.15, lift=0)]
    if fire:
        effects += [node('deal_damage', amount=2*fire, element='fire'), node('modify_temperature', amount=40*fire)]
    if cold:
        effects += [node('modify_temperature', amount=-40*cold)]
    if fire and cold:
        effects += [node('conditional', condition='was_frozen', effects=[node('deal_damage', amount=4*(fire+cold), element='thermal')])]
    if bolt:
        effects += [node('deal_damage', amount=2*bolt, element='lightning'), node('add_charge', amount=35*bolt)]
        if not self_cast:
            effects += [node('chain_targets', count=2+bolt*2, range=3+bolt, amount=2*bolt)]
    if earth:
        if self_cast:
            effects += [status('stone_shell', 100+earth*40, earth), status('chilled', 80+earth*30, earth)]
        else:
            effects += [node('deal_damage', amount=earth*2, element='earth'), node('apply_force', amount=.25*earth, lift=.12*earth), node('destroy_blocks', budget=earth*3, radius=.5*earth)]
    if life:
        effects += [node('heal', amount=life*2), status('regenerating', 80+life*40, life)]
        if not self_cast: effects += [node('grow_plants', budget=life*2)]
    if arcane:
        if not self_cast: effects += [node('deal_damage', amount=arcane*2, element='arcane')]
        effects += [status('arcane_unstable', 60+arcane*30, arcane)]
    if water and earth and not self_cast: effects += [status('rooted', 30+earth*10)]
    if fire and water and not self_cast: effects += [status('obscured', 50)]
    if earth and life and not self_cast: effects += [status('rooted', 40+earth*20)]
    if cold and life and not self_cast: effects += [status('stone_shell', 80, life)]
    if life and arcane and not self_cast:
        effects += [node('revive', health_fraction=.3), node('remove_status', status='burning')]
    return effects

def snake(text): return re.sub(r'(?<!^)(?=[A-Z])', '_', text).lower()
def slug(text): return re.sub('[^a-z0-9]+', '_', text.lower()).strip('_')

rows = []
for line in (ROOT/'Minecraft_Magicka_All_329_Recipes_v1.0.md').read_text(encoding='utf-8').splitlines():
    c = [v.strip() for v in line.strip().strip('|').split('|')]
    if len(c) == 8 and c[0].isdigit(): rows.append(dict(zip(['number','formula','tier','name','tags','shape_origin','primary','self_description'], c)))
assert len(rows) == 329
name_counts = collections.Counter(r['name'] for r in rows)
index, seen = [], set()
construction_shapes = {'platform','pillar','wall','bridge','stair','ramp','spike','growth'}
for row in rows:
    formula = {}
    for part in row['formula'].split(' + '):
        match = re.fullmatch(r'([A-Za-z]+)(?:\u00d7(\d+))?', part)
        assert match, part
        formula[match[1].lower()] = int(match[2] or 1)
    key = tuple(formula.get(e,0) for e in ELEMENTS)
    assert key not in seen and 1 <= sum(key) <= 4
    seen.add(key)
    size = sum(key)
    rid = slug(row['name'])
    if name_counts[row['name']] > 1: rid += '_' + '_'.join(e+str(formula[e]) for e in ELEMENTS if e in formula)
    shape, origin = (snake(v.strip()) for v in row['shape_origin'].split('/'))
    weigh = lambda weights, base: base + sum(weights[e]*n for e, n in formula.items())
    delivery = dict(radius=round(weigh(dict(fire=.5,water=.8,cold=.6,lightning=.4,earth=.5,life=.7,arcane=.6),1.3),1), speed=.75+size*.08,
                    duration_ticks=int(round(weigh(dict(fire=25,water=50,cold=40,lightning=25,earth=45,life=55,arcane=35),80)/20)*20), interval_ticks=20,
                    gravity=.025 if 'earth' in formula else 0, burst_radius=round(weigh(dict(fire=.7,water=.4,cold=.4,lightning=.5,earth=.5,life=.3,arcane=.6),1.5),1))
    material = 'ice' if 'cold' in formula else 'root' if 'life' in formula else 'earth'
    config = dict(material=material, budget=[0,1,3,8,16,28][size], interval_ticks=2 if material=='ice' else 4, width=1, height=3, floating=False, integrity=4+size*2)
    effects = generic(formula)
    self_mode, self_effects = 'generic', generic(formula, True)
    if shape in construction_shapes:
        effects = [node('create_construction')]
    elif shape in ('targeted_zone','mine','summon_object'):
        # The shape owns duration and periodic delivery; this graph is applied on each pulse.
        effects = [e for e in effects if e['type'] != 'destroy_blocks']
    name = row['name']
    if name in ('Stone Platform','Stone Stair','Skybridge','Moving Bastion','Floating Platform'): config['material']='earth'
    if name in ('Crystal Anchor','Crystal Frost'): config['material']='arcane'; delivery['object_kind']='anchor'; effects=[node('create_construction')]
    if name in ('Skybridge','Floating Platform','Crystal Anchor','Crystal Frost'): config['floating']=True
    if name in ('Root Ledge','Living Wall','Living Bulwark','Living Bridge','Hibernating Roots'):
        config['material']='root';config['regenerates']=True
    if name in ('Stone Platform','Root Ledge'): config['width']=3;config['budget']=9
    if name=='Ice Step': config.update(material='ice',budget=1);self_mode='construct';self_effects=[status('chilled',60)]
    if name=='Stone Platform': self_mode='construct';self_effects=[]
    if name=='Moving Bastion': config['width']=1; config['budget']=5; self_mode='construct';self_effects=[]
    if name=='Floating Platform': config['width']=3;config['budget']=9
    if name=='Root Ledge': self_effects=[node('heal',amount=2),status('rooted',80)]
    if name in ('Living Bulwark','Living Wall','Living Bridge'): self_effects=[status('stone_shell',200,3),status('chilled',160,2),status('regenerating',200,2)] + ([node('set_wet',duration_ticks=120)] if name=='Living Bridge' else [])
    if name in ('Brittle Cage','Hibernating Roots'): effects += [status('rooted',120),status('brittle',120)];self_mode='construct'
    if name in ('Ice Spear','Glacier Hammer'): effects += [status('rooted',50),status('brittle',100)]
    if name in ('Thermal Fracture','Glacier Hammer'): effects += [node('brittle_terrain',radius=2)]
    if name in ('Preservation Field','Sanctuary','Hibernating Roots'):
        effects += [status('stone_shell',60,2),node('remove_status',status='burning')]
        if name!='Hibernating Roots': effects=[e for e in effects if e['type'] not in ('deal_damage','apply_force')]
    if name in ('Preservation Field','Stasis Field'): self_effects=[status('chilled',120,4),status('stone_shell',120,2)]+([node('heal',amount=2)] if name=='Preservation Field' else [])
    if name in ('Stasis Field','Static Stasis'): delivery['slow_projectiles']=True
    if name in ('Healing Pool','Regeneration Mist','Healing Spring','Guardian Stone','Sanctuary','Mend Burst') or (set(formula)=={'life'}):
        effects=[e for e in effects if e['type'] not in ('deal_damage','apply_force','add_charge') and e.get('status')!='arcane_unstable']
    if name=='Healing Pool': self_effects=[node('heal',amount=4),node('set_wet',duration_ticks=160),status('regenerating',160,2)]
    if name=='Mend Burst': self_effects=[node('heal',amount=6),status('regenerating',120,2)]
    if name in ('Healing Spring','Guardian Stone'): delivery['object_kind']='healer';delivery['object_health']=12;self_mode='summon'
    if name=='Guardian Stone': delivery['heal_weakest']=True; config['material']='earth'
    if name in ('Bubble Lift','Buoyant Bubble'):
        delivery['object_kind']='bubble';effects=[node('set_wet',duration_ticks=80),status('levitating',100),status('slow_falling',180)];self_mode='summon';self_effects=effects
    if name in ('Steam Mine','Arcane Detonation','Thunder Nova','Plasma Detonation','Fire Nova Bomb'): self_mode='nova'
    if name in ('Regeneration Mist','Static Stasis','Shock Field','Sanctuary','Conductive Flood','Flooded Circuit'): self_mode='zone'
    if name in ('Overcharge','Haste Surge'):
        effects=[status('haste',100 if name=='Overcharge' else 180,2),node('delay',ticks=100 if name=='Overcharge' else 180,effects=[node('add_charge',amount=45),status('chilled',50,1)])];self_mode='effects';self_effects=effects
    if name in ('Phoenix Leap','Firebomb'):
        self_mode='dash'; self_effects=generic(formula,True)
        if name=='Phoenix Leap': delivery['trail']=True;delivery['dash_speed']=1.2
    if name in ('Arcane Meteor',): delivery['meteor']=True;delivery['delay_ticks']=12;self_mode='meteor'
    if name=='Volatile Magma Core': delivery['delay_ticks']=12;delivery['shards']=4;effects += [node('spawn_zone',duration_ticks=100,radius=2)]
    if name in ('Molten Boulder','Magma Boulder'): delivery['shards']=3;effects += [node('spawn_zone',duration_ticks=100,radius=2)]
    if name=='Plasma Orb': delivery['speed']=.3;delivery['volatile']=True;delivery['burst_radius']=3
    if name=='Volcanic Vent': effects += [node('apply_force',amount=.1,lift=.9)];delivery['shards']=2;self_mode='zone'
    if name in ('Flash Freeze Lake','Frozen Ground','Conductive Ice'):
        delivery['ice_surface']=True;config['material']='ice';config['budget']=16
    if name=='Frozen Ground': self_mode='zone'
    if name=='Quicksilt': effects += [status('rooted',60,2)]
    if name=='Tesla Spire': delivery['conductive']=True;effects=[node('create_construction'),node('chain_targets',count=4,range=5,amount=2),node('add_charge',amount=35)]
    if name in ('Brittle Cage','Hibernating Roots'): self_effects=effects
    if name=='Ice Spear': self_effects=[status('stone_shell',160,2),status('chilled',160,3),status('brittle',100)]
    if name=='Glacier Hammer': self_effects=[status('stone_shell',200,3),status('chilled',200,5),status('brittle',140)]
    if name=='Crystal Frost': self_effects=[status('stone_shell',160,2),status('chilled',120,2),status('brittle',120)]
    if name=='Arcane Detonation': effects += [node('apply_force',amount=1.2,lift=.3)];self_effects=effects
    if name=='Regeneration Mist': effects += [status('obscured',40)];self_effects=effects
    if name in ('Conductive Flood','Flooded Circuit','Static Stasis','Shock Field','Sanctuary','Frozen Ground','Volcanic Vent'): self_effects=effects
    if name=='Phoenix Leap': self_effects=[node('deal_damage',amount=2,element='fire'),node('modify_temperature',amount=25)]
    if name=='Crystal Anchor': self_effects=generic(formula,True)+[status('rooted',60)]
    if name=='Thermal Fracture': self_effects=generic(formula,True)+[node('deal_damage',amount=4,element='thermal')]
    if name=='Plasma Orb': self_effects=generic(formula,True)+[node('delay',ticks=40,effects=[node('deal_damage',amount=5,element='arcane')])]
    recipe=dict(id='magicka:'+rid, formula=formula, name=name, tier=row['tier'], tags=row['tags'], shape=shape, origin=origin,
                cast_ticks=[0,5,9,14,20,27][size], delivery=delivery, construction=config, effects=effects,
                self=dict(mode=self_mode,effects=self_effects), primary=row['primary'], self_description=row['self_description'])
    if name=='Ice Ramp': recipe.update(name='Ice Spike',tags='BUILD/MOVE/DMG',shape='spike',cast_ticks=14,construction=dict(config,budget=12,interval_ticks=1,integrity=10,impale_damage=5))
    recipe=spell_design.apply(recipe)
    replacement=SUPPORT.get(name) or BUILD.get(tuple(sorted(formula.items())))
    if replacement:
        replaced=ROOT/'src/main/resources/data/magicka/magicka_spell'/f'{rid}.json'
        if not CHECK and replaced.exists(): replaced.unlink()
        recipe=json.loads(json.dumps(replacement));assert recipe['formula']==formula,name;rid=recipe['id'].split(':')[1]
    target=ROOT/'src/main/resources/data/magicka/magicka_spell'/f'{rid}.json';target.parent.mkdir(parents=True,exist_ok=True)
    output(target,recipe);index.append(recipe['id'])
expected=set()
for size in range(1,5):
    for combo in itertools.combinations_with_replacement(range(7),size): expected.add(tuple(combo.count(i) for i in range(7)))
assert seen == expected and len(set(index))==329
target=ROOT/'src/main/resources/assets/magicka/spell_index.json'
output(target,index)
print(f'Imported {len(index)} recipes: {dict(collections.Counter(r["tier"] for r in rows))}')
