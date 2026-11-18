"""Validate the exact staged/tracked source snapshot before publishing it."""
from pathlib import Path
import json
import re
import subprocess

root = Path(__file__).resolve().parents[1]
top = Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], cwd=root, text=True, encoding='utf8').strip())
if top.resolve() != root.resolve():
    raise SystemExit('Run this check in the standalone Guanzhi repository, not a parent repository')
names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode('utf8').strip('\0').split('\0')
forbidden_dirs = {'.tools', '.venv', '.gradle', 'vendor', 'build', 'dist', 'model-source', 'allint-source', 'backups', '__pycache__'}
forbidden_suffixes = {'.gguf', '.safetensors', '.bin', '.pt', '.pth', '.ckpt', '.onnx', '.keystore', '.jks', '.p12', '.pfx', '.pem', '.key', '.apk', '.aab', '.ipa', '.zip', '.rar', '.7z', '.exe', '.dll', '.so', '.class', '.pyc', '.log'}
secret = re.compile(rb'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
errors = []
total = 0
for name in names:
    if not name:
        continue
    path = Path(name)
    if any(part in forbidden_dirs for part in path.parts) or path.suffix.lower() in forbidden_suffixes:
        errors.append(name + ': excluded artifact')
    if path.name == '.env' or (path.name.startswith('.env.') and path.name != '.env.example'):
        errors.append(name + ': environment file')
    # Read the Git index, not just the working copy.
    data = subprocess.check_output(['git', 'show', ':' + name], cwd=root)
    total += len(data)
    if len(data) > 10 * 1024 * 1024:
        errors.append(name + ': unexpectedly large source file')
    if data.startswith(b'GGUF') or secret.search(data):
        errors.append(name + ': weight or credential signature')
    if b':\\Users\\' in data or b':/Users/' in data:
        # This validator contains these checks, but must not expose actual local file paths.
        if name != 'scripts/check_repository.py':
            errors.append(name + ': local absolute path')
if errors:
    raise SystemExit('\n'.join(errors))
print(json.dumps({'checked_files': len(names), 'source_bytes': total, 'weights_or_credentials_found': False}))
