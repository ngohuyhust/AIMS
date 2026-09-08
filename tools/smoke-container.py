#!/usr/bin/env python3
"""Smoke test a built image against a fresh disposable DB; never load .env or real providers."""
import argparse
import json
import os
import secrets
import subprocess
import time
import urllib.request
import urllib.error
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--image', default='aims-backend:local')
args = parser.parse_args()
prefix = 'aims-smoke-' + uuid.uuid4().hex[:10]
network, database, backend = prefix, prefix + '-db', prefix + '-api'
env = dict(os.environ, POSTGRES_PASSWORD=secrets.token_hex(24), POSTGRES_USER='aims_test', POSTGRES_DB='aims_test')
env.update(AIMS_DB_PASSWORD=env['POSTGRES_PASSWORD'], AIMS_DB_USERNAME='aims_test',
           AIMS_DB_URL=f'jdbc:postgresql://{database}:5432/aims_test', JWT_SECRET=secrets.token_hex(32))

def docker(*words):
    return subprocess.check_output(['docker', *words], env=env, text=True, stderr=subprocess.PIPE).strip()

def wait_for(action, timeout=120):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            if action():
                return
        except (subprocess.CalledProcessError, OSError):
            pass
        time.sleep(1)
    raise RuntimeError('Isolated container readiness timed out')

try:
    docker('network', 'create', network)
    docker('run', '-d', '--name', database, '--network', network, '-e', 'POSTGRES_PASSWORD',
           '-e', 'POSTGRES_USER', '-e', 'POSTGRES_DB', 'postgres:17.6-alpine')
    wait_for(lambda: 'accepting connections' in docker('exec', database, 'pg_isready', '-U', 'aims_test', '-d', 'aims_test'))
    docker('run', '-d', '--name', backend, '--network', network, '--read-only', '--tmpfs', '/tmp',
           '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '-p', '127.0.0.1::3000',
           '-e', 'AIMS_DB_URL', '-e', 'AIMS_DB_USERNAME', '-e', 'AIMS_DB_PASSWORD', '-e', 'JWT_SECRET',
           '-e', 'SPRING_PROFILES_ACTIVE=production', '-e', 'APP_PUBLIC_URL=https://shop.example.test',
           '-e', 'NOTIFICATIONS_ENABLED=false', '-e', 'VIETQR_ENABLE_TEST_CALLBACK=false', args.image)
    wait_for(lambda: docker('inspect', '-f', '{{.State.Health.Status}}', backend) == 'healthy')
    assert docker('inspect', '-f', '{{.Config.User}}', backend) == '10001:10001'
    port = docker('port', backend, '3000/tcp').rsplit(':', 1)[1]
    for path, expected in [('/actuator/health', 200), ('/api/products', 200), ('/api/orders/pending', 401)]:
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{port}{path}', timeout=5) as response:
                status, body = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, body = error.code, error.read()
        assert status == expected, (path, status)
        if path == '/actuator/health':
            assert json.loads(body) == {'status': 'UP'}
        elif path == '/api/products':
            assert json.loads(body) == []
    assert docker('exec', database, 'psql', '-U', 'aims_test', '-d', 'aims_test', '-tAc',
                  'select count(*) from flyway_schema_history where success') == '10'
    print('PASS: production image, non-root/read-only runtime, PostgreSQL/Flyway V1–V10, health/catalog/auth boundary.')
finally:
    for name in [backend, database]:
        subprocess.run(['docker', 'rm', '-f', '-v', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(['docker', 'network', 'rm', network], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
