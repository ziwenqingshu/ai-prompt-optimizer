#!/usr/bin/env python3
"""Run one real-model optimization and verify persistence, feedback, and cleanup.

The operator must configure a real provider; a LIVE flag alone is not evidence of
real inference. This script does not create a fixture or fall back to mock.
"""
import json
import os
import sys
import time
import urllib.request
import urllib.error

BASE = os.environ.get('BACKEND_URL', 'http://127.0.0.1:8080').rstrip('/')


def request(path, method='GET', body=None):
    req = urllib.request.Request(BASE + path, method=method,
        data=None if body is None else json.dumps(body).encode(),
        headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=180) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        # Only print this app's machine-readable code, never arbitrary HTTP bodies.
        try:
            code = json.loads(error.read()).get('code', 'HTTP_ERROR')
        except (ValueError, AttributeError):
            code = 'HTTP_ERROR'
        raise RuntimeError(f'HTTP {error.code}: {code}') from None


def main():
    status = request('/api/model/status')
    if status.get('mode') != 'LIVE' or not status.get('configured'):
        sys.exit('未配置真实模型：请填写 .env 并重启服务。本次没有发起模型请求。')
    history_id = None
    started = time.monotonic()
    try:
        result = request('/api/prompts/optimize', 'POST', {
            'originalPrompt': '帮我写一个 Python 函数，对整数列表排序，说明输入输出并给出测试用例。',
            'scene': 'CODE', 'style': 'PROFESSIONAL'})
        history_id = result.get('historyId')
        assert history_id and result['mode'] == 'LIVE' and result['optimizedPrompt'].strip(), '模型响应或历史 ID 无效'
        assert len(result['suggestions']) == 3, '建议数量不正确'
        elapsed = round(time.monotonic() - started, 2)
        assert any(item['id'] == history_id for item in request('/api/prompts/history')), '历史未保存'
        request(f'/api/prompts/history/{history_id}/feedback', 'PUT', {'rating': 5})
        saved = next(item for item in request('/api/prompts/history') if item['id'] == history_id)
        assert saved['rating'] == 5, '评分未保存'
        assert not result['degraded'], '模型格式已降级，请检查服务商 JSON 输出支持'
    finally:
        if history_id:
            request(f'/api/prompts/history/{history_id}', 'DELETE')
    assert all(item['id'] != history_id for item in request('/api/prompts/history')), '删除未生效'
    print(json.dumps({'passed': True, 'model': result['model'], 'mode': result['mode'],
        'degraded': result['degraded'], 'seconds': elapsed,
        'checks': ['optimize', 'history', 'feedback', 'delete']}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, AssertionError, urllib.error.URLError, TimeoutError, ValueError, KeyError) as error:
        sys.exit(f'验证失败：{error}')
