import importlib.util,json,unittest,tempfile,threading,urllib.request,urllib.error
from pathlib import Path
spec=importlib.util.spec_from_file_location('editor',Path(__file__).parents[1]/'slash-anchor-editor/server.py');editor=importlib.util.module_from_spec(spec);spec.loader.exec_module(editor)
class EditorTest(unittest.TestCase):
 def test_resolve(self):
  self.assertIn('rotten_ruins:true_copper',editor.models())
  for name in ('true_copper','domerite_sword'):
   item=editor.load_item('rotten_ruins:'+name);editor.validate(item['profile']);self.assertTrue(item['sprite'].startswith('data:image/png;base64,'))
 def test_project_catalog(self):
  items=editor.catalog();self.assertGreater(len(items),100)
  self.assertEqual({x['id'] for x in items if x['slashEnabled']},{'rotten_ruins:true_copper','rotten_ruins:domerite_sword','rotten_ruins:domerite_longsword'})
  self.assertTrue(all(x['name'] and Path(x['model']).is_file() for x in items))
  self.assertTrue(editor.item_icon('rotten_ruins:true_copper').is_file())
 def test_reject_paths(self):
  for identifier in ('../evil:x','..:x','rotten_ruins:../x','rotten_ruins:/x','rotten_ruins:a//b','rotten_ruins:C:/windows','x:./x'):
   with self.assertRaises(ValueError):editor.profile_path(identifier,'source')
 def test_api_save(self):
  with tempfile.TemporaryDirectory() as folder:
   old=editor.GAME;editor.GAME=Path(folder);server=editor.ThreadingHTTPServer(('127.0.0.1',0),editor.Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
   base=f'http://127.0.0.1:{server.server_port}'
   try:
    data=json.load(urllib.request.urlopen(base+'/api/items'));self.assertEqual(data['token'],editor.TOKEN)
    p=editor.load_item('rotten_ruins:true_copper')['profile'];body=json.dumps({'id':'rotten_ruins:test_sword','target':'config','profile':p}).encode()
    def request(token):return urllib.request.urlopen(urllib.request.Request(base+'/api/save',body,headers={'Content-Type':'application/json','X-Editor-Token':token,'Origin':base}))
    with self.assertRaises(urllib.error.HTTPError) as error:request('wrong')
    self.assertEqual(error.exception.code,403)
    path=Path(json.load(request(editor.TOKEN))['path']);self.assertTrue(path.is_relative_to(Path(folder)));self.assertEqual(json.loads(path.read_text()),p)
    request(editor.TOKEN).close();self.assertTrue(path.with_suffix('.json.bak').is_file())
   finally:server.shutdown();server.server_close();thread.join();editor.GAME=old
 def test_invalid_profiles(self):
  for points in ([],[[0,0,0]],[[0,0,0],[float('nan'),0,0]],[[0,0,0],[999,0,0]]):
   with self.assertRaises(ValueError):editor.validate({'points':points})
 def test_effect_roundtrip(self):
  with tempfile.TemporaryDirectory() as folder:
   old=editor.GAME;editor.GAME=Path(folder);server=editor.ThreadingHTTPServer(('127.0.0.1',0),editor.Handler);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
   base=f'http://127.0.0.1:{server.server_port}'
   try:
    for name in ('true_copper','domerite_sword','domerite_longsword'):editor.validate_effect(editor.load_effect('rotten_ruins:'+name)['effect'])
    p=dict(editor.SCHEMA['defaults'],width=2.5,brightness=2,refraction=7.25,dispersion=.35,stability=1)
    body=json.dumps({'id':'rotten_ruins:effect_test','target':'config','profile':p}).encode()
    request=urllib.request.Request(base+'/api/save-effect',body,headers={'X-Editor-Token':editor.TOKEN,'Origin':base,'Content-Type':'application/json'})
    path=Path(json.load(urllib.request.urlopen(request))['path']);self.assertTrue(path.is_relative_to(Path(folder)));self.assertEqual(editor.load_effect('rotten_ruins:effect_test')['effect'],p)
    bad=dict(p,opacity=5)
    with self.assertRaises(ValueError):editor.validate_effect(bad)
    shader=json.load(urllib.request.urlopen(base+'/api/effect-schema'));self.assertIn('TrailShape',shader['fragment'])
    directory=urllib.request.Request(base+'/api/game-dir',json.dumps({'path':folder}).encode(),headers={'X-Editor-Token':editor.TOKEN,'Origin':base,'Content-Type':'application/json'})
    self.assertEqual(Path(json.load(urllib.request.urlopen(directory))['gameDir']),Path(folder).resolve())
    relative=urllib.request.Request(base+'/api/game-dir',json.dumps({'path':'relative/path'}).encode(),headers={'X-Editor-Token':editor.TOKEN,'Origin':base,'Content-Type':'application/json'})
    with self.assertRaises(urllib.error.HTTPError):urllib.request.urlopen(relative)
   finally:server.shutdown();server.server_close();thread.join();editor.GAME=old
if __name__=='__main__':unittest.main()
