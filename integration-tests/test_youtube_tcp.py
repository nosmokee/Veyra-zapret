"""Loopback-only native strategy smoke tests; NOT proof of YouTube/provider bypass.
Run: python3 integration-tests/test_youtube_tcp.py
Builds the checked-in Android ByeDPI sources with the host C compiler. No APK/NDK.
"""
import ast
import hashlib
import os
from pathlib import Path
import re
import shlex
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'android/app/src/main/cpp/byedpi'
PRESETS = ROOT / 'android/app/src/main/java/io/github/dovecoteescapee/byedpi/core/VeyraPresets.kt'


def commands():
    values = {}
    pattern = r'(?:private )?const val (\w+) = ((?:"[^"\n]*"|\w+)(?:\s*\+\s*(?:"[^"\n]*"|\w+))*)'
    def evaluate(node):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            return node.value
        if isinstance(node, ast.Name):
            return values[node.id]
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
            return evaluate(node.left) + evaluate(node.right)
        raise ValueError('Unsupported preset expression')
    for name, expression in re.findall(pattern, PRESETS.read_text()):
        values[name] = evaluate(ast.parse(expression.replace('\n', ' '), mode='eval').body)
    return values


def read_exact(stream, length):
    result = bytearray()
    while len(result) < length:
        chunk = stream.recv(length - len(result))
        if not chunk:
            raise EOFError('Unexpected EOF')
        result.extend(chunk)
    return bytes(result)


class YouTubeTcpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.folder = Path(cls.temp.name)
        cls.binary = cls.folder / 'ciadpi'
        sources = ['main.c', 'packets.c', 'conev.c', 'proxy.c', 'desync.c', 'mpool.c', 'extend.c']
        subprocess.run(['cc', '-D_DEFAULT_SOURCE', '-std=c99', '-O2', '-I', str(NATIVE),
                        *[str(NATIVE / s) for s in sources], '-o', str(cls.binary)], check=True,
                       capture_output=True)
        subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes',
                        '-keyout', str(cls.folder/'key.pem'), '-out', str(cls.folder/'cert.pem'),
                        '-days', '1', '-subj', '/CN=loopback-test'], check=True, capture_output=True)
        cls.presets = commands()

    @classmethod
    def tearDownClass(cls):
        cls.temp.cleanup()

    def test_base_is_unchanged(self):
        self.assertEqual('--ip 127.0.0.1 --port 1080 --timeout 3 --disorder 1+s '
                         '--auto=torst --split 1+s --tlsrec 3+s', self.presets['YOUTUBE_COMMAND'])

    def round_trip(self, rejected_handshakes):
        errors = []
        seen_sni = []
        payload = bytes(range(256)) * 12288  # 3 MiB over several seconds, not just HTTPS headers.
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0))
        listener.listen()
        listener.settimeout(12)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(self.folder/'cert.pem', self.folder/'key.pem')
        context.set_servername_callback(lambda sock, name, ctx: seen_sni.append(name))

        def serve():
            try:
                for _ in range(rejected_handshakes):
                    with listener.accept()[0] as raw:
                        raw.settimeout(8)
                        self.assertTrue(raw.recv(16384))
                        # Orderly TLS EOF must trigger ssl_err fallback, not leak to the client.
                        raw.shutdown(socket.SHUT_WR)
                        while raw.recv(16384):
                            pass
                with listener.accept()[0] as raw:
                    raw.settimeout(8)
                    with context.wrap_socket(raw, server_side=True) as tls:
                        request = bytearray()
                        while b'\r\n\r\n' not in request:
                            request.extend(tls.recv(4096))
                        tls.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: ' + str(len(payload)).encode() + b'\r\n\r\n')
                        for offset in range(0, len(payload), 65536):
                            tls.sendall(payload[offset:offset+65536])
                            time.sleep(0.065)
            except BaseException as exc:
                errors.append(exc)
            finally:
                listener.close()

        with socket.socket() as probe:
            probe.bind(('127.0.0.1', 0))
            port = probe.getsockname()[1]
        args = shlex.split(self.presets['YOUTUBE_TCP_COMMAND'])
        args[args.index('--port')+1] = str(port)
        log = self.folder / f'proxy-{rejected_handshakes}.log'
        with log.open('w') as output:
            process = subprocess.Popen([str(self.binary), *args, '--debug', '2'], stdout=output, stderr=output)
            thread = threading.Thread(target=serve, daemon=True)
            thread.start()
            try:
                for _ in range(100):
                    try:
                        raw = socket.create_connection(('127.0.0.1', port), timeout=1)
                        break
                    except OSError:
                        if process.poll() is not None:
                            self.fail(log.read_text())
                        time.sleep(0.03)
                else:
                    self.fail('Proxy did not start')
                with raw:
                    raw.settimeout(15)
                    raw.sendall(b'\x05\x01\x00')
                    self.assertEqual(b'\x05\x00', read_exact(raw, 2))
                    raw.sendall(b'\x05\x01\x00\x01\x7f\x00\x00\x01' + listener.getsockname()[1].to_bytes(2, 'big'))
                    reply = read_exact(raw, 4)
                    self.assertEqual(0, reply[1])
                    self.assertIn(reply[3], (1, 4))
                    read_exact(raw, (4 if reply[3] == 1 else 16)+2)
                    client_context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
                    client_context.check_hostname = False
                    client_context.verify_mode = ssl.CERT_NONE  # Self-signed loopback server only.
                    with client_context.wrap_socket(raw, server_hostname='rr1.test.googlevideo.com') as tls:
                        tls.sendall(b'GET /videoplayback HTTP/1.1\r\nHost: rr1.test.googlevideo.com\r\n\r\n')
                        data = bytearray()
                        while b'\r\n\r\n' not in data:
                            data.extend(tls.recv(4096))
                        header, body = bytes(data).split(b'\r\n\r\n', 1)
                        self.assertIn(b'200 OK', header)
                        remaining = read_exact(tls, len(payload)-len(body))
                        self.assertEqual(hashlib.sha256(payload).digest(), hashlib.sha256(body+remaining).digest())
                thread.join(15)
                self.assertFalse(thread.is_alive(), 'Server did not finish')
                self.assertEqual([], errors)
                self.assertEqual(['rr1.test.googlevideo.com'], seen_sni)
            finally:
                process.terminate()
                try:
                    process.wait(3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                listener.close()
        text = log.read_text()
        for attempt in range(rejected_handshakes+1):
            self.assertIn(f'desync params index: {attempt + getattr(self, "tcp_group_offset", 0)}', text)

    def test_primary_stream(self):
        self.round_trip(0)

    def test_tls_eof_fallback(self):
        self.round_trip(1)

    def test_second_tls_eof_fallback(self):
        self.round_trip(2)


if __name__ == '__main__':
    unittest.main(verbosity=2)
