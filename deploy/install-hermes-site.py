#!/usr/bin/env python3
"""Install the isolated website adapter; does not change the personal Hermes gateway.

Run as root with --source-dir pointing to an uploaded deploy directory.
The website is switched only with --activate after the adapter passes its real inference check.
"""
import argparse
import grp
import os
from pathlib import Path
import pwd
import secrets
import shutil
import subprocess
from urllib.parse import urlsplit
from datetime import datetime, timezone


def read_env(path):
    result = {}
    for line in path.read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            result[key] = value.strip().strip('"').strip("'")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--activate", action="store_true")
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("Run with sudo")
    env_path = Path("/etc/maimai/maimai.env")
    site_env = Path("/etc/maimai/hermes-site.env")
    values = read_env(env_path)
    if args.activate:
        site = read_env(site_env)
        backup = env_path.with_name("maimai.env.before-hermes-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S"))
        shutil.copy2(env_path, backup)
        changes = {"MAIMAI_HERMES_BASE_URL": "http://127.0.0.1:8643/v1", "MAIMAI_HERMES_TOKEN": site["MAIMAI_SITE_TOKEN"],
                   "MAIMAI_HERMES_MODEL": "hermes-maizai/" + site["MAIMAI_SITE_MODEL"], "MAIMAI_HERMES_READONLY_VERIFIED": "true"}
        lines = [line for line in env_path.read_text().splitlines() if line.split("=", 1)[0] not in changes]
        env_path.write_text("\n".join(lines + [f"{key}={value}" for key, value in changes.items()]) + "\n")
        os.chmod(env_path, 0o640)
        print("Website endpoint updated; backup=" + str(backup) + "; restart maimai during release activation.")
        return
    runtime = Path("/opt/hermes/runtime")
    if not (runtime / "run_agent.py").is_file() or not (runtime / "venv/bin/python").exists():
        raise SystemExit("Installed Hermes runtime not found; no personal config will be modified")
    try:
        account = pwd.getpwnam("maimai-hermes")
    except KeyError:
        subprocess.run(["useradd", "--system", "--home-dir", "/var/lib/maimai-hermes", "--shell", "/usr/sbin/nologin", "maimai-hermes"], check=True)
        account = pwd.getpwnam("maimai-hermes")
    directory = Path("/opt/maimai-hermes")
    directory.mkdir(mode=0o755, exist_ok=True)
    (directory / "runtime").mkdir(mode=0o755, exist_ok=True)
    (directory / "python").mkdir(mode=0o755, exist_ok=True)
    shutil.copy2(args.source_dir / "hermes-site-gateway.py", directory / "hermes-site-gateway.py")
    if not site_env.exists():
        provider = urlsplit(values.get("MAIMAI_HERMES_BASE_URL", ""))
        if (provider.scheme != "https" or provider.hostname not in {"api.minimaxi.com", "api.minimax.io"}
                or provider.username or provider.password or provider.query or provider.fragment
                or provider.path not in {"", "/", "/v1", "/v1/"} or provider.port not in {None, 443}):
            raise SystemExit("Provider configuration changed; inspect before copying approved credentials")
        settings = {"MAIMAI_SITE_TOKEN": secrets.token_urlsafe(48), "MAIMAI_SITE_PROVIDER_URL": values["MAIMAI_HERMES_BASE_URL"],
                    "MAIMAI_SITE_PROVIDER_KEY": values["MAIMAI_HERMES_TOKEN"], "MAIMAI_SITE_MODEL": values["MAIMAI_HERMES_MODEL"]}
        if any("\n" in v or "\r" in v or not v for v in settings.values()):
            raise SystemExit("Invalid provider configuration")
        site_env.write_text("\n".join(f"{key}={value}" for key, value in settings.items()) + "\n")
    os.chmod(site_env, 0o640)
    os.chown(site_env, 0, account.pw_gid)
    state = Path("/var/lib/maimai-hermes")
    state.mkdir(mode=0o700, exist_ok=True)
    os.chown(state, account.pw_uid, account.pw_gid)
    config = state / "config.yaml"
    if not config.exists():
        config.write_text("agent:\n  api_max_retries: 1\nmemory:\n  memory_enabled: false\n  user_profile_enabled: false\nproviders:\n  custom:\n    request_timeout_seconds: 25\n    stale_timeout_seconds: 25\n")
        os.chmod(config, 0o600)
        os.chown(config, account.pw_uid, account.pw_gid)
    shutil.copy2(args.source_dir / "systemd/maimai-hermes.service", "/etc/systemd/system/maimai-hermes.service")
    subprocess.run(["systemctl", "daemon-reload"], check=True)
    subprocess.run(["systemctl", "enable", "--now", "maimai-hermes"], check=True)
    print("Isolated website service installed; personal Hermes and current website endpoint are unchanged.")


if __name__ == "__main__":
    main()
