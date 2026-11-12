import argparse
import concurrent.futures
import json
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / 'src/main/resources/assets/magicka/lang'
CACHE = ROOT / 'test-jars/localization-cache'
LANGUAGES = {'de_de': 'de', 'es_es': 'es', 'fr_fr': 'fr', 'pt_br': 'pt', 'zh_cn': 'zh-CN', 'ja_jp': 'ja'}
PROTECTED = re.compile(r'%(?:\d+\$)?[sdif]|%%|§.|\b(?:Spellweaver|HUD|Shift|Ctrl|Alt|Esc|LMB|RMB)\b|\b[a-z_]+:[a-z0-9_/*]+|\d+(?:[.,]\d+)?|[❤⚡✦×≈]|^#\s*|^•\s*')
TOKEN = re.compile(r'⟦\s*(\d+)\s*⟧')
FORMAT = re.compile(r'%(?:\d+\$)?[sdif]|%%')

def protect(value):
    saved = []
    def replace(match):
        saved.append(match[0])
        return '⟦'+str(len(saved)-1)+'⟧'
    return PROTECTED.sub(replace, value), saved

def restore(value, saved):
    found = TOKEN.findall(value)
    if sorted(map(int, found)) != list(range(len(saved))):
        raise ValueError('Changed protected tokens: '+repr(value))
    return TOKEN.sub(lambda match: saved[int(match[1])], value).strip()

def request(text, language):
    query = urllib.parse.urlencode({'client': 'gtx', 'sl': 'en', 'tl': language, 'dt': 't', 'q': text})
    req = urllib.request.Request('https://translate.googleapis.com/translate_a/single?'+query, headers={'User-Agent': 'Mozilla/5.0'})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=45) as response:
                data = json.load(response)
            return ''.join(part[0] for part in data[0] if part[0])
        except Exception:
            if attempt == 4:
                raise
            time.sleep(2**attempt)

def translate_single(value, language):
    protected,saved=protect(value)
    try:
        return restore(request(protected,language),saved)
    except ValueError:
        parts=re.split(r'([.!?;:,]\s+)',value)
        if len(parts)==1:
            raise
        return ''.join(part if index%2 else translate_single(part,language) for index,part in enumerate(parts) if part)

def translate_batch(values, language):
    protected = [protect(value) for value in values]
    query = '\n'.join('[[%04d]] %s' % (index, value[0]) for index,value in enumerate(protected))
    translated = request(query, language)
    markers = list(re.finditer(r'[\[［]{1,2}\s*(\d{4})\s*[\]］]{1,2}\s*', translated))
    if len(markers) != len(values) or [int(marker[1]) for marker in markers] != list(range(len(values))):
        if len(values) == 1:
            return [translate_single(values[0],language)]
        middle = len(values)//2
        return translate_batch(values[:middle], language) + translate_batch(values[middle:], language)
    results = []
    for index, marker in enumerate(markers):
        value = translated[marker.end():markers[index+1].start() if index+1<len(markers) else len(translated)].strip()
        try:
            results.append(restore(value, protected[index][1]))
        except ValueError:
            if len(values) == 1:
                return [translate_single(values[0],language)]
            results.append(translate_single(values[index],language))
    return results

def chunks(values):
    batch = []
    length = 0
    for value in values:
        size = len(protect(value)[0])+12
        if batch and length+size > 3400:
            yield batch
            batch,length=[],0
        batch.append(value)
        length += size
    if batch:
        yield batch

def generate(locale, source, fetch):
    cache_path = CACHE / (locale+'.json')
    cache = json.loads(cache_path.read_text(encoding='utf-8')) if cache_path.exists() else {}
    lines = list(dict.fromkeys(line for value in source.values() for line in value.split('\n') if line))
    missing = [line for line in lines if line not in cache]
    if missing and not fetch:
        raise ValueError(locale+': missing cached translations; use --fetch only with permission to send game text to Google Translate')
    batches = list(chunks(missing))
    for index,batch in enumerate(batches):
        translations = translate_batch(batch, LANGUAGES[locale])
        for original,translated in zip(batch,translations):
            if FORMAT.findall(original) != FORMAT.findall(translated):
                raise ValueError(locale+' format mismatch: '+original)
            if not translated:
                raise ValueError(locale+' empty translation: '+original)
            cache[original] = translated
        CACHE.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps(cache,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(locale, '%s/%s batches' % (index+1,len(batches)), flush=True)
        time.sleep(.35)
    translated = {key:'\n'.join(cache.get(line,'') for line in value.split('\n')) for key,value in source.items()}
    common_path = ROOT / 'tools/localization/common.json'
    if common_path.exists():
        common = json.loads(common_path.read_text(encoding='utf-8'))
        column = list(LANGUAGES).index(locale)
        for key,value in source.items():
            translated[key] = '\n'.join(common[line][column] if line in common else cache.get(line,'') for line in value.split('\n'))
        add = ['Hinzufügen: %s','Añadir: %s','Ajouter : %s','Adicionar: %s','添加：%s','追加：%s'][column]
        for element in ['arcane','cold','death','earth','fire','life','lightning','water']:
            translated['key.magicka.'+element] = add % translated['element.magicka.'+element]
    overrides_path = ROOT / 'tools/localization' / (locale+'.json')
    if overrides_path.exists():
        translated.update(json.loads(overrides_path.read_text(encoding='utf-8')))
    for key,value in source.items():
        if FORMAT.findall(value) != FORMAT.findall(translated[key]):
            raise ValueError(locale+' invalid formatting: '+key)
        if len(value)>45 and ' ' in value and translated[key]==value:
            raise ValueError(locale+' untranslated text: '+key)
    (LANG / (locale+'.json')).write_text(json.dumps(translated,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(locale, 'complete:',len(translated),'keys',flush=True)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--locales',nargs='+',choices=list(LANGUAGES),default=list(LANGUAGES))
    parser.add_argument('--probe',action='store_true')
    parser.add_argument('--fetch',action='store_true')
    args = parser.parse_args()
    if args.probe:
        values = ['Inner padding','Width %s%% · Alt+wheel','• Damage 1.5 ❤ fire (×0.5 on wet)','On you: heal %2$s ❤.']
        for locale in ['es_es','zh_cn','ja_jp']:
            print(json.dumps({locale:translate_batch(values,LANGUAGES[locale])},ensure_ascii=True),flush=True)
        return
    source = json.loads((LANG/'en_us.json').read_text(encoding='utf-8'))
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        futures = {pool.submit(generate,locale,source,args.fetch):locale for locale in args.locales}
        for future in concurrent.futures.as_completed(futures):
            future.result()

if __name__ == '__main__':
    main()
