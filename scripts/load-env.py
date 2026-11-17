#!/usr/bin/env python3
"""Load project dotenv as literal values (never shell code), then exec a command."""
import os
from pathlib import Path
import re
import sys

ALLOWED = {
    'LLM_MODE', 'LLM_BASE_URL', 'LLM_API_KEY', 'LLM_MODEL', 'LLM_JSON_MODE',
    'LLM_CONNECT_TIMEOUT_SECONDS', 'LLM_READ_TIMEOUT_SECONDS',
    'APP_PORT', 'APP_BIND_ADDRESS', 'APP_DATA_DIR', 'BACKEND_PORT', 'FRONTEND_PORT',
}


def read_env(path):
    values = {}
    if not path.exists():
        return values
    for number, line in enumerate(path.read_text(encoding='utf-8-sig').splitlines(), 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('export '):
            line = line[7:].strip()
        key, separator, value = line.partition('=')
        key, value = key.strip(), value.strip()
        if not separator or not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', key):
            raise ValueError(f'.env 第 {number} 行格式错误，应为 KEY=value')
        if key not in ALLOWED:
            raise ValueError(f'.env 第 {number} 行包含不支持的配置项')
        if value.startswith(('"', "'")):
            if len(value) < 2 or value[-1] != value[0]:
                raise ValueError(f'.env 第 {number} 行引号不匹配')
            value = value[1:-1]
        else:
            value = re.split(r'\s+#', value, maxsplit=1)[0].rstrip()
        values[key] = value
    return values


if __name__ == '__main__':
    try:
        values = read_env(Path(sys.argv[1]))
    except (OSError, ValueError) as error:
        # Do not include the content of the file in error messages.
        print(str(error) if isinstance(error, ValueError) else '无法读取 .env 配置文件', file=sys.stderr)
        sys.exit(1)
    environment = {**values, **os.environ}
    environment['PROMPT_OPTIMIZER_ENV_LOADED'] = '1'
    os.execvpe(sys.argv[2], sys.argv[2:], environment)
