import csv, json, pathlib, subprocess
root = pathlib.Path(__file__).resolve().parents[3]
flow = root / '.workflow/android-modularization-20261018'
base = json.loads((flow / 'evidence/baseline-inventory.json').read_text())
scope = list(csv.DictReader((flow / 'production-scope.csv').open()))
vendor = {
 'app/src/main/assets/generative-libs/guizang/lucide.min.js',
 'app/src/main/assets/generative-libs/guizang/motion.min.js',
 'app/src/main/assets/generative-libs/guizang/three.min.js',
 'app/src/main/assets/generative-libs/vchart.min.js',
 'app/src/main/assets/generative-libs/ui-generation.js',
 'app/src/main/assets/generative-libs/ui-generation.css',
 'highlight/src/main/res/raw/prism.js',
}
def own(path):
    p = pathlib.PurePosixPath(path)
    if any(s in p.parts for s in ('build', 'target', 'node_modules', '.gradle', '.workflow')):
        return False
    if path in vendor or path.startswith('document/src/main/java/com/artifex/'):
        return False
    return (
        ('/src/main/' in '/' + path and p.suffix in ('.kt','.java','.js','.html','.css'))
        or (path.startswith('web-ui/app/') and p.suffix in ('.ts','.tsx','.css'))
        or (path.startswith('native/') and '/src/' in path
            and p.suffix == '.rs' and '/bin/' not in path)
    )
def lines(path):
    data = (root / path).read_bytes()
    return data.count(b'\n') + int(bool(data) and not data.endswith(b'\n'))
paths = sorted(set(subprocess.check_output(
    ['git','ls-files','-co','--exclude-standard','-z']
).decode().strip('\0').split('\0')))
current = [{'path':p, 'lines':lines(p)} for p in paths
           if own(p) and (root / p).is_file()]
def show(label, rows):
    print(label, 'files=',len(rows), 'lines=',sum(r['lines'] for r in rows),
          '>1000=',sum(r['lines']>1000 for r in rows),
          '>1500=',sum(r['lines']>1500 for r in rows),
          'max=',max(rows,key=lambda r:r['lines']))
def summary(rows):
    return {'files':len(rows),'lines':sum(r['lines'] for r in rows),
            'over1000':sum(r['lines']>1000 for r in rows),
            'over1500':sum(r['lines']>1500 for r in rows),
            'largest':max(rows,key=lambda r:r['lines'])}
result={'baselineAllText':summary(base),
        'baselineOwnProduction':summary([r for r in base if own(r['path'])]),
        'currentOwnProduction':summary(current),
        'currentLargeOwnProduction':sorted([r for r in current if r['lines']>1000],key=lambda r:r['lines'],reverse=True),
        'originalScope':[dict(r,current_lines=lines(r['path']) if (root/r['path']).is_file() else None) for r in scope]}
assert len(scope)==52 and len({r['path'] for r in scope})==52
result['originalScopeAllPresent']=all(r['current_lines'] is not None for r in result['originalScope'])
(flow/'evidence/final-source-inventory.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k not in ('originalScope','currentLargeOwnProduction')},ensure_ascii=False))
