"""Local TCP/UDP regression for fast-start preset. No real YouTube/ISP test."""
import unittest
from test_youtube_tcp import YouTubeTcpTest, commands, ROOT
from test_quic_fake import QuicFakeTest
import test_quic_fake

class FastStartTcpTest(YouTubeTcpTest):
 tcp_group_offset=1
 @classmethod
 def setUpClass(cls):
  super().setUpClass()
  cls.presets['YOUTUBE_TCP_COMMAND']=cls.presets['YOUTUBE_FAST_START_COMMAND'].replace('@VEYRA_QUIC_FAKE@',str(ROOT/'android/app/src/main/assets/quic_initial_www_google_com.bin'))

class FastStartUdpTest(QuicFakeTest):
 @classmethod
 def setUpClass(cls):
  super().setUpClass()
  def fast_commands():
   values=commands();values['YOUTUBE_QUIC_FAKE_COMMAND']=values['YOUTUBE_FAST_START_COMMAND'];return values
  cls.original=test_quic_fake.commands;test_quic_fake.commands=fast_commands
 @classmethod
 def tearDownClass(cls):
  test_quic_fake.commands=cls.original;super().tearDownClass()

if __name__=='__main__':
 suite=unittest.TestSuite([unittest.defaultTestLoader.loadTestsFromTestCase(c) for c in (FastStartTcpTest,FastStartUdpTest)])
 result=unittest.TextTestRunner(verbosity=2).run(suite)
 raise SystemExit(not result.wasSuccessful())
