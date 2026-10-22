import pathlib,tempfile,json,unittest,sys
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]));import app
class Base13Tests(unittest.TestCase):
 def test_fresh_default_and_version(self):
  with tempfile.TemporaryDirectory() as d:
   c=app.Controller(d);self.assertEqual('1.3',c.state()['version']);self.assertEqual('base',c.profile);self.assertEqual('Veyra Base',app.PROFILES['base'][0])
 def test_upgrade_preserves_secret_and_manual_choice_after_migration(self):
  with tempfile.TemporaryDirectory() as d:
   p=pathlib.Path(d);(p/'settings.json').write_text(json.dumps({'profile':'youtube','theme':'light','color':'amber','background':'none'}));(p/'telegram-private.json').write_text('private-sentinel')
   c=app.Controller(d);self.assertEqual('base',c.profile);self.assertEqual('light',c.theme);c.save()
   c.profile='youtube';c.save();self.assertEqual('youtube',app.Controller(d).profile);self.assertEqual('private-sentinel',(p/'telegram-private.json').read_text())
 def test_base_has_tcp_fallback_and_no_quic_claim(self):
  args=app.PROFILES['base'][1];self.assertEqual(2,args.count('--auto=torst,ssl_err'));self.assertEqual('5',args[args.index('--timeout')+1]);self.assertNotIn('--udp-fake',args)
