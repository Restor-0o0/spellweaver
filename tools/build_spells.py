"""Structure spells that replace weak generated variants on the same formulas. Imported by import_spell_catalog.py."""
from support_spells import spell


def structure(key, formula, name, template, budget, batch, material='earth', integrity=12):
    recipe = spell(key, formula, name, 'wall', [{'type': 'create_construction'}], [], origin='caster_feet')
    recipe.update(tags='BUILD', construction={'material': material, 'budget': budget, 'interval_ticks': 1, 'batch': batch, 'width': 1, 'height': 3,
                                              'floating': False, 'integrity': integrity, 'template': template, 'uncapped': True})
    recipe['self']['mode'] = 'construct'
    return recipe


BUILD = [
    structure('stone_wall', {'earth': 1, 'arcane': 2}, 'Stone Wall', 'wall', 110, 6),
    structure('stone_slab', {'earth': 1, 'arcane': 3}, 'Stone Slab', 'slab', 25, 5),
    structure('ice_wall', {'cold': 1, 'earth': 2, 'arcane': 1}, 'Ice Wall', 'wall', 110, 6, material='ice', integrity=10),
    structure('slanted_wall', {'earth': 1, 'life': 1, 'arcane': 2}, 'Slanted Wall', 'slope', 80, 6),
    structure('earthen_redoubt', {'water': 1, 'earth': 1, 'arcane': 2}, 'Earthen Redoubt', 'redoubt', 80, 6),
]
BY_FORMULA = {tuple(sorted(r['formula'].items())): r for r in BUILD}
