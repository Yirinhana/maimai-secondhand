"""Apply an approved website-only Tina endpoint from a private candidate file.

Read-only by default. This preserves every non-AI setting and backs up before apply.
The endpoint must have been independently verified to exclude personal Agent tools.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import shutil

FIELDS = {'MAIMAI_HERMES_BASE_URL', 'MAIMAI_HERMES_TOKEN', 'MAIMAI_HERMES_MODEL', 'MAIMAI_HERMES_READONLY_VERIFIED'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--candidate', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if os.name == 'nt' or os.geteuid() != 0:
        parser.error('Requires the authorized deployment host root context')
    candidate = Path(args.candidate)
    env = Path('/etc/maimai/maimai.env')
    if candidate.parent != env.parent or candidate.is_symlink() or env.is_symlink():
        raise RuntimeError('Unexpected configuration paths')
    for file in [candidate, env]:
        if file.stat().st_uid != 0 or file.stat().st_mode & 0o077:
            raise RuntimeError('Configuration must be root-owned and private')
    values = json.loads(candidate.read_text())
    if set(values) != FIELDS or any(not isinstance(v, str) or not v or '\n' in v or '\r' in v or '\x00' in v for v in values.values()):
        raise RuntimeError('Invalid candidate fields; values suppressed')
    if values['MAIMAI_HERMES_BASE_URL'] != 'https://api.minimaxi.com/v1' or values['MAIMAI_HERMES_MODEL'] != 'MiniMax-M3' or values['MAIMAI_HERMES_READONLY_VERIFIED'] != 'true':
        raise RuntimeError('Candidate differs from the independently verified endpoint')
    original = env.read_bytes()
    lines = original.decode().splitlines(keepends=True)
    kept = [line for line in lines if line.split('=', 1)[0] not in FIELDS]
    content = ''.join(kept).rstrip('\r\n') + '\n' + ''.join(k + '=' + json.dumps(values[k], ensure_ascii=False) + '\n' for k in sorted(FIELDS))
    print(json.dumps({'endpoint': values['MAIMAI_HERMES_BASE_URL'], 'model': values['MAIMAI_HERMES_MODEL'], 'secretDisplayed': False, 'status': 'CANDIDATE_VALID'}))
    if not args.apply:
        return
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    backup_dir = Path('/var/backups/maimai') / ('tina-env-' + stamp)
    backup_dir.mkdir(mode=0o700)
    backup = backup_dir / 'maimai.env'
    shutil.copy2(env, backup)
    backup.chmod(0o600)
    temporary = env.with_name('maimai.env.tina-next-' + stamp)
    with open(temporary, 'x', opener=lambda name, flags: os.open(name, flags, 0o600)) as out:
        out.write(content)
        out.flush()
        os.fsync(out.fileno())
    if env.read_bytes() != original:
        raise RuntimeError('Configuration changed during preparation; candidate retained, active file untouched')
    os.replace(temporary, env)
    print('AI_FIELDS_APPLIED; restart the verified application release to activate')


if __name__ == '__main__':
    main()
