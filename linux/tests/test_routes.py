import pathlib,sys,threading,time,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import telegram_bridge as tg
class Socket:
 def __init__(self):self.closed=False
 def close(self):self.closed=True
class Routes(unittest.TestCase):
 def test_priority_media_cache_and_distinct(self):
  for dc in (2,4):
   for media in (False,True):
    cached=(f'kws{dc}-1.web.telegram.org','149.154.167.220');c=tg.candidates(dc,media,cached);main=f'kws{dc}.web.telegram.org'
    self.assertEqual(c[:2],[(main,'149.154.167.220'),(main,main)]);self.assertIn(cached,c);self.assertEqual(len(c),len(set(c)))
  self.assertEqual(tg.candidates(3,True)[0][0],'kws3-1.web.telegram.org');self.assertRaises(ValueError,tg.candidates,203)
 def test_fast_winner_and_late_loser(self):
  release=threading.Event();late=Socket();winner=Socket()
  def slow(track):release.wait(2);return late
  def fast(track):return winner
  t=time.monotonic();self.assertIs(tg.first_connected([slow,fast]),winner);self.assertLess(time.monotonic()-t,.5);self.assertFalse(winner.closed);release.set()
  for _ in range(100):
   if late.closed:break
   time.sleep(.01)
  self.assertTrue(late.closed)
 def test_inflight_cancel(self):
  tracked=Socket();ready=threading.Event();release=threading.Event();winner=Socket()
  def slow(track):track(tracked);ready.set();release.wait(2);return Socket()
  def fast(track):ready.wait(1);return winner
  self.assertIs(tg.first_connected([slow,fast]),winner);self.assertTrue(tracked.closed);release.set()
 def test_failure_does_not_cancel_winner(self):
  def fail(track):raise OSError('synthetic')
  winner=Socket();self.assertIs(tg.first_connected([fail,lambda track:winner]),winner);self.assertFalse(winner.closed)
 def test_all_failures(self):
  def fail(track):raise OSError('synthetic')
  self.assertRaises(OSError,tg.first_connected,[fail,fail])
 def test_stop_and_late_success(self):
  stop=threading.Event();ready=threading.Event();resource=Socket()
  def attempt(track):track(resource);ready.set();stop.set();return Socket()
  self.assertRaises(OSError,tg.first_connected,[attempt],stop.is_set);self.assertTrue(resource.closed)
