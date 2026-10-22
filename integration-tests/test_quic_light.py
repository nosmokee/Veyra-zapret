"""Loopback-only regression for UDP fake-count A/B. No latency/YouTube test."""
import unittest
from test_youtube_tcp import YouTubeTcpTest, commands, ROOT
from test_quic_fake import QuicFakeTest
import test_quic_fake

class QuicLightTcpTest(YouTubeTcpTest):
 tcp_group_offset=1
 @classmethod
 def setUpClass(cls):
  super().setUpClass()
  cls.presets['YOUTUBE_TCP_COMMAND']=cls.presets['YOUTUBE_QUIC_LIGHT_COMMAND'].replace('@VEYRA_QUIC_FAKE@',str(ROOT/'android/app/src/main/assets/quic_initial_www_google_com.bin'))

class QuicLightUdpTest(QuicFakeTest):
 fake_count=3
 @classmethod
 def setUpClass(cls):
  super().setUpClass()
  def light_commands():
   values=commands();values['YOUTUBE_QUIC_FAKE_COMMAND']=values['YOUTUBE_QUIC_LIGHT_COMMAND'];return values
  cls.original=test_quic_fake.commands;test_quic_fake.commands=light_commands
 @classmethod
 def tearDownClass(cls):
  test_quic_fake.commands=cls.original;super().tearDownClass()

if __name__=='__main__':
 suite=unittest.TestSuite([unittest.defaultTestLoader.loadTestsFromTestCase(c) for c in (QuicLightTcpTest,QuicLightUdpTest)])
 result=unittest.TextTestRunner(verbosity=2).run(suite)
 raise SystemExit(not result.wasSuccessful())
