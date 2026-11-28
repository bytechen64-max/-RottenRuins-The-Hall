"""Offline, loopback-only model/texture anchor editor. Python 3.10+, Pillow."""
from __future__ import annotations
import argparse, base64, io, json, math, mimetypes, os, re, secrets, tempfile, webbrowser
from pathlib import Path
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WEB = Path(__file__).resolve().parent
ASSETS = [ROOT / 'src/main/resources/assets', ROOT / 'src/generated/resources/assets']
TOKEN = secrets.token_urlsafe(24)
GAME = ROOT / 'run'
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')

def split_id(value):
    if ':' not in value: value = 'minecraft:' + value
    if not ID.fullmatch(value) or value.split(':',1)[0] in ('.','..') or any(p in ('..', '.', '') for p in value.split(':', 1)[1].split('/')):
        raise ValueError('Invalid resource ID')
    return value.split(':', 1)

def asset(identifier, kind, suffix):
    namespace, name = split_id(identifier)
    for root in ASSETS:
        path = root / namespace / kind / (name + suffix)
        if path.is_file(): return path
    return None

def models():
    ids = set()
    for root in ASSETS:
        for path in root.glob('*/models/item/**/*.json'):
            relative = path.relative_to(root)
            ids.add(relative.parts[0] + ':' + '/'.join(relative.parts[3:])[:-5])
    return sorted(ids)

def catalog():
    """Discover current resource models and source registrations; no fixed weapon list."""
    translations={}
    for root in reversed(ASSETS):
        for path in root.glob('*/lang/zh_cn.json'):
            translations.update(json.loads(path.read_text(encoding='utf-8-sig')))
    java=ROOT/'src/main/java'
    slash_classes=set()
    declarations=[]
    for path in java.rglob('*.java'):
        text=path.read_text(encoding='utf-8-sig')
        match=re.search(r'\bclass\s+(\w+)([^{}]*)\{',text)
        if match: declarations.append((match.group(1),match.group(2)))
    for _ in range(8):
        for name,parents in declarations:
            if 'ISlashWeapon' in parents or any(re.search(r'\b'+c+r'\b',parents) for c in slash_classes): slash_classes.add(name)
    enabled=set()
    for path in java.rglob('*Register*Item*.java'):
        text=path.read_text(encoding='utf-8-sig')
        for match in re.finditer(r'ITEMS\.register\(\s*"([a-z0-9_/.-]+)"\s*,([^;]+);',text):
            if any(re.search(r'\b'+c+r'\b',match.group(2)) for c in slash_classes): enabled.add('rotten_ruins:'+match.group(1))
    result=[]
    for identifier in models():
        ns,name=split_id(identifier)
        label=translations.get('item.'+ns+'.'+name,translations.get('block.'+ns+'.'+name,name))
        label=re.sub(r'§[0-9a-fk-or]|\[rrs:[^\]]*\]|\[/\]','',label,flags=re.I)
        result.append({'id':identifier,'name':label,
            'slashEnabled':identifier in enabled,'anchors':asset(identifier,'slash_anchors','.json') is not None,
            'effect':asset(identifier,'slash_effects','.json') is not None,
            'model':str(asset(ns+':item/'+name,'models','.json'))})
    return sorted(result,key=lambda x:(not x['slashEnabled'],x['name'],x['id']))

def item_icon(identifier):
    ns,name=split_id(identifier);textures=resolve(ns+':item/'+name).get('textures',{})
    for key in ('layer0',*textures):
        path=texture_ref('#'+key,textures)
        if path: return path
    return None

def resolve(identifier, seen=None):
    seen = set() if seen is None else seen
    if identifier in seen: raise ValueError('Model parent cycle')
    seen.add(identifier)
    path = asset(identifier, 'models', '.json')
    if not path: return {'textures': {}, 'elements': [], 'parent': identifier}
    own = json.loads(path.read_text(encoding='utf-8-sig'))
    parent = resolve(own['parent'], seen) if own.get('parent') and not own['parent'].startswith('builtin/') else {}
    merged = {**parent, **own}
    merged['textures'] = {**parent.get('textures', {}), **own.get('textures', {})}
    merged['display'] = {**parent.get('display', {}), **own.get('display', {})}
    return merged

def data_url(path):
    return 'data:image/png;base64,' + base64.b64encode(path.read_bytes()).decode()

def texture_ref(ref, textures):
    seen = set()
    while ref.startswith('#'):
        if ref in seen: return None
        seen.add(ref); ref = textures.get(ref[1:], '')
    return asset(ref, 'textures', '.png') if ref else None

def guess(path):
    if not path: return [[4, 4, 8], [14, 14, 8]]
    image = Image.open(path).convert('RGBA'); w, h = image.size
    points = [(x+.5, y+.5) for y in range(h) for x in range(w) if image.getpixel((x,y))[3] > 128]
    if len(points)<2: return [[4,4,8],[14,14,8]]
    mx=sum(x for x,y in points)/len(points); my=sum(y for x,y in points)/len(points)
    xx=sum((x-mx)**2 for x,y in points); yy=sum((y-my)**2 for x,y in points); xy=sum((x-mx)*(y-my) for x,y in points)
    angle=.5*math.atan2(2*xy,xx-yy); dx,dy=math.cos(angle),math.sin(angle)
    ordered=sorted(points,key=lambda p:(p[0]-mx)*dx+(p[1]-my)*dy)
    endpoints=[ordered[int((len(ordered)-1)*q)] for q in (.32,.98)]
    result=[[round(x/w*16,3),round(16-y/h*16,3),8] for x,y in endpoints]
    # Blade root first, tip last. Most Minecraft swords point toward the texture's upper right.
    if result[0][1]>result[1][1]: result.reverse()
    return result

def profile_path(identifier, target, kind='slash_anchors'):
    ns, name = split_id(identifier)
    root = ASSETS[0]/ns/kind if target=='source' else GAME/'config/rotten_ruins'/kind/ns
    path = (root/(name+'.json')).resolve()
    if not path.is_relative_to(root.resolve()): raise ValueError('Path outside anchor directory')
    return path

def validate(profile):
    for key in ('firstPerson','thirdPerson'):
        view = profile.get(key, {}); points = view.get('points', profile.get('points', []))
        if not 2<=len(points)<=16: raise ValueError('Each view needs 2..16 ordered anchors')
        for point in points:
            if len(point)!=3 or any(not isinstance(n,(int,float)) or not math.isfinite(n) or abs(n)>256 for n in point): raise ValueError('Invalid anchor')
        values={'start':.32,'end':.62,'lifetimeTicks':7,'maxDistance':4,'maxSamples':128,'subdivisions':4}
        values.update({k:view[k] for k in values if k in view})
        if any(not isinstance(n,(int,float)) or not math.isfinite(n) for n in values.values()): raise ValueError('Non-finite setting')
        if not (0<=values['start']<values['end']<=1 and .1<=values['lifetimeTicks']<=200 and 0<values['maxDistance']<=64 and 2<=values['maxSamples']<=1024 and 1<=values['subdivisions']<=32): raise ValueError('Invalid sampling setting')

def load_item(identifier):
    ns,name=split_id(identifier); model=resolve(ns+':item/'+name); textures=model.get('textures',{})
    images={}
    for key in textures:
        path=texture_ref('#'+key,textures)
        if path: images[key]=data_url(path)
    sprite=texture_ref('#layer0',textures) or next((texture_ref('#'+k,textures) for k in textures if texture_ref('#'+k,textures)),None)
    profile=None; origin='未保存'
    for target in ('source','config'):
        path=profile_path(identifier,target)
        if path.is_file(): profile=json.loads(path.read_text(encoding='utf-8-sig'));origin=str(path)
    if profile is None:
        view={'enabled':True,'points':guess(sprite),'start':.32,'end':.62,'lifetimeTicks':7,'maxDistance':4,'maxSamples':128,'subdivisions':4}
        profile={'version':1,'firstPerson':view,'thirdPerson':json.loads(json.dumps(view))}
    return {'id':identifier,'model':model,'images':images,'sprite':data_url(sprite) if sprite else None,'profile':profile,'origin':origin,'guess':guess(sprite)}

SCHEMA = json.loads((WEB/'effect-schema.json').read_text(encoding='utf-8'))

def validate_effect(profile):
    if not isinstance(profile, dict): raise ValueError('Effect must be a JSON object')
    if profile.get('renderer','shader') not in ('shader','texture','inherit'): raise ValueError('Unknown renderer')
    if profile.get('color','inherit')!='inherit' and not re.fullmatch(r'#[0-9a-fA-F]{6}',profile.get('color','')): raise ValueError('Color must be #RRGGBB')
    split_id(profile.get('texture',SCHEMA['defaults']['texture']))
    for key,(default,minimum,maximum,step) in SCHEMA['parameters'].items():
        value=profile.get(key,default)
        if isinstance(value,bool) or not isinstance(value,(int,float)) or not math.isfinite(value) or not minimum<=value<=maximum: raise ValueError('Invalid effect field: '+key)

def load_effect(identifier):
    split_id(identifier); profile=dict(SCHEMA['defaults']); origin='默认效果'
    for target in ('source','config'):
        path=profile_path(identifier,target,'slash_effects')
        if path.is_file(): profile.update(json.loads(path.read_text(encoding='utf-8-sig'))); origin=str(path)
    return {'id':identifier,'effect':profile,'origin':origin}

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args): pass
    def respond(self, status, data, content='application/json; charset=utf-8'):
        if not isinstance(data, bytes): data=json.dumps(data,ensure_ascii=False).encode()
        self.send_response(status);self.send_header('Content-Type',content);self.send_header('Content-Length',str(len(data)))
        self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(data)
    def do_GET(self):
        try:
            url=urlparse(self.path); query=parse_qs(url.query)
            if url.path=='/api/catalog': self.respond(200,{'items':catalog(),'project':str(ROOT)});return
            if url.path=='/api/icon':
                path=item_icon(query['id'][0]);self.respond(200,path.read_bytes() if path else base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg=='),'image/png');return
            if url.path=='/api/effect': self.respond(200,load_effect(query['id'][0]));return
            if url.path=='/api/effect-schema':
                result=dict(SCHEMA);result['fragment']=(ROOT/'src/main/resources/assets/rotten_ruins/shaders/core/slash_trail.fsh').read_text(encoding='utf-8')
                result['airFragment']=(ROOT/'src/main/resources/assets/rotten_ruins/shaders/core/slash_air.fsh').read_text(encoding='utf-8');result['texture']=data_url(asset('rotten_ruins:effect/slash_crescent','textures','.png'));result['spark']=data_url(asset('rotten_ruins:effect/slash_spark','textures','.png'));self.respond(200,result);return
            if url.path=='/api/texture':
                namespace,name=split_id(query['id'][0]);name=name.removeprefix('textures/').removesuffix('.png')
                path=asset(namespace+':'+name,'textures','.png')
                if not path: raise ValueError('Texture not found')
                self.respond(200,{'url':data_url(path)});return
            if url.path=='/api/items': self.respond(200,{'items':models(),'token':TOKEN,'gameDir':str(GAME)});return
            if url.path=='/api/item': self.respond(200,load_item(query['id'][0]));return
            filename={'/':'effects.html','/effects':'effects.html','/effect.js':'effect.js','/sprite-geometry.js':'sprite-geometry.js','/effects.css':'effects.css','/anchors':'index.html','/app.js':'app.js','/style.css':'style.css'}.get(url.path)
            if not filename: self.respond(404,{'error':'Not found'});return
            self.respond(200,(WEB/filename).read_bytes(),mimetypes.guess_type(filename)[0]+'; charset=utf-8')
        except Exception as error: self.respond(400,{'error':str(error)})
    def do_POST(self):
        global GAME
        try:
            host=self.headers.get('Host',''); origin=self.headers.get('Origin')
            if host!=f'127.0.0.1:{self.server.server_port}' or (origin and origin!='http://'+host) or self.headers.get('X-Editor-Token')!=TOKEN: self.respond(403,{'error':'Same-origin token required'});return
            size=int(self.headers.get('Content-Length','0'))
            if self.path not in ('/api/save','/api/save-effect','/api/game-dir') or not 0<size<65536: raise ValueError('Invalid save request')
            body=json.loads(self.rfile.read(size));is_effect=self.path=='/api/save-effect'
            if self.path=='/api/game-dir':
                path=Path(body['path']).expanduser()
                if not path.is_absolute() or not path.is_dir(): raise ValueError('请选择已存在的游戏实例绝对路径')
                GAME=path.resolve();self.respond(200,{'gameDir':str(GAME)});return
            (validate_effect if is_effect else validate)(body['profile'])
            if body['target'] not in ('source','config'): raise ValueError('Invalid target')
            path=profile_path(body['id'],body['target'],'slash_effects' if is_effect else 'slash_anchors');path.parent.mkdir(parents=True,exist_ok=True)
            if path.exists(): path.with_suffix('.json.bak').write_bytes(path.read_bytes())
            text=json.dumps(body['profile'],ensure_ascii=False,indent=2)+'\n'
            with tempfile.NamedTemporaryFile('w',encoding='utf-8',dir=path.parent,delete=False) as f: f.write(text);temp=f.name
            os.replace(temp,path);self.respond(200,{'path':str(path)})
        except Exception as error: self.respond(400,{'error':str(error)})

def main():
    global GAME
    parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=8765);parser.add_argument('--game-dir',type=Path,default=GAME);parser.add_argument('--no-open',action='store_true');args=parser.parse_args()
    GAME=args.game_dir.resolve();server=ThreadingHTTPServer(('127.0.0.1',args.port),Handler)
    url=f'http://127.0.0.1:{server.server_port}'
    print('Slash anchor editor: '+url,flush=True)
    if not args.no_open: webbrowser.open(url)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()

if __name__=='__main__': main()
