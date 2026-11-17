import importlib.util
from pathlib import Path
import os
import subprocess
import socket
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'load-env.py'
spec = importlib.util.spec_from_file_location('load_env', SCRIPT)
loader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(loader)


class EnvTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / '.env'

    def test_missing_file_uses_defaults(self):
        self.assertEqual(loader.read_env(self.path), {})

    def test_values_are_literal_never_shell_commands(self):
        marker = Path(self.temp.name) / 'should-not-exist'
        self.path.write_text(f'LLM_API_KEY="$(touch {marker})"\nLLM_MODEL=some-model # comment\n')
        data = loader.read_env(self.path)
        self.assertEqual(data['LLM_API_KEY'], f'$(touch {marker})')
        self.assertEqual(data['LLM_MODEL'], 'some-model')
        self.assertFalse(marker.exists())

    def test_export_quotes_crlf_and_hash_in_key(self):
        self.path.write_bytes(b'\xef\xbb\xbf# comment\r\nexport LLM_MODE="live"\r\nLLM_API_KEY=abc#def\r\n')
        self.assertEqual(loader.read_env(self.path), {'LLM_MODE': 'live', 'LLM_API_KEY': 'abc#def'})

    def test_error_does_not_include_secret(self):
        for text in ['LLM_API_KEY="secret', 'secret without equals', 'PATH=secret']:
            self.path.write_text(text)
            with self.assertRaises(ValueError) as result:
                loader.read_env(self.path)
            self.assertNotIn('secret', str(result.exception))

    def test_environment_takes_precedence_and_is_exported(self):
        self.path.write_text('LLM_MODE=live\nLLM_MODEL=fixture-model\n')
        env = {k: v for k, v in os.environ.items() if k not in {'LLM_MODE', 'LLM_MODEL'}}
        env['LLM_MODE'] = 'mock'
        result = subprocess.run([sys.executable, str(SCRIPT), str(self.path), sys.executable, '-c',
            'import os; print(os.environ["LLM_MODE"], os.environ["LLM_MODEL"])'], env=env, capture_output=True, text=True, check=True)
        self.assertEqual(result.stdout.strip(), 'mock fixture-model')

    def test_launcher_rejects_occupied_port_before_starting_services(self):
        root = SCRIPT.parent.parent
        with socket.socket() as occupied, socket.socket() as other:
            occupied.bind(('127.0.0.1', 0))
            other.bind(('127.0.0.1', 0))
            env = {**os.environ, 'APP_ENV_FILE': str(self.path), 'HOST': '127.0.0.1',
                   'BACKEND_PORT': str(occupied.getsockname()[1]), 'FRONTEND_PORT': str(other.getsockname()[1])}
            env.pop('PROMPT_OPTIMIZER_ENV_LOADED', None)
            result = subprocess.run(['bash', str(root / 'scripts/run-local.sh')], env=env,
                                    capture_output=True, text=True, timeout=5)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Cannot bind', result.stderr)


if __name__ == '__main__':
    unittest.main()
