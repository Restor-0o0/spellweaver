"""Shape the power curve by formula size: 1-3 elements support, 4 damage, 5 ultimate.

Run after add_fifth_element.py and before localize_spells.py. The marker keeps a
second run from scaling the same numbers twice.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPELLS = ROOT / 'src/main/resources/data/magicka/magicka_spell'
DAMAGE = {1: 0.6, 2: 0.7, 3: 0.9, 4: 1.2, 5: 1.3}
HEAL = {1: 1.3, 2: 1.25, 3: 1.15, 4: 1.0, 5: 1.0}
CONTROL = {1: 1.35, 2: 1.25, 3: 1.1, 4: 1.0, 5: 1.0}
CONTROLLED = {'apply_status', 'apply_effect', 'set_wet', 'polymorph', 'summon_undead', 'raise_dead'}


def half(value):
    return max(0.5, round(value * 2) / 2)


def scale(node, size):
    kind = node.get('type')
    if kind in ('deal_damage', 'drain_life') and isinstance(node.get('amount'), (int, float)):
        node['amount'] = half(node['amount'] * DAMAGE[size])
    if kind == 'chain_targets' and isinstance(node.get('amount'), (int, float)):
        node['amount'] = half(node['amount'] * DAMAGE[size])
    if kind == 'heal' and isinstance(node.get('amount'), (int, float)):
        node['amount'] = half(node['amount'] * HEAL[size])
    if kind in CONTROLLED and isinstance(node.get('duration_ticks'), (int, float)):
        node['duration_ticks'] = min(72000, round(node['duration_ticks'] * CONTROL[size]))
    for key in ('effects', 'else_effects'):
        for child in node.get(key, []):
            scale(child, size)


def main():
    touched = 0
    for path in sorted(SPELLS.glob('*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        if data.get('balanced'):
            continue
        size = sum(data['formula'].values())
        for node in data['effects']:
            scale(node, size)
        for node in data.get('self', {}).get('effects', []):
            scale(node, size)
        data['balanced'] = True
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        touched += 1
    print(touched, 'spells rebalanced by tier')


main()
