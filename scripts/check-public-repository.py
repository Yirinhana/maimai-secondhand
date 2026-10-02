#!/usr/bin/env python3
"""Check indexed public content without printing matched private values."""
from __future__ import annotations

import argparse
import io
import re
import subprocess
import sys
import zipfile
from pathlib import Path, PurePosixPath

PUBLIC_DOCUMENTS = {
    "1-组号-组员姓名-题目名称--需求分析.docx",
    "2-组号-组员姓名-题目名称--系统设计.docx",
    "3-组号-组员姓名-题目名称--专周报告.docx",
    "4-组号-组员姓名-题目名称--操作说明书.docx",
    "docs/policies/麦麦二手 用户协议与交易售后规则 草案.docx",
}
PRIVATE_SUFFIXES = {".pem", ".key", ".p12", ".pfx", ".db", ".sqlite", ".sqlite3", ".bundle", ".zip", ".pdf"}
PATTERNS = {
    "private-key": re.compile(r"-----BEGIN (?:RSA |OPENSSH |EC |DSA )?PRIVATE KEY-----"),
    "provider-token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{25,}|github_pat_[A-Za-z0-9_]{30,}|sk-[A-Za-z0-9_-]{25,}|g2a_[A-Za-z0-9_-]{20,}|AKIA[0-9A-Z]{16})"),
    "student-id": re.compile(r"学号\s*[:：]?\s*\d{8,14}(?!\d)"),
}


def inspect(path: str, data: bytes) -> list[str]:
    p = PurePosixPath(path)
    reasons = []
    if any(part in {".local", ".ssh", ".workbuddy"} for part in p.parts):
        reasons.append("private-directory")
    if p.name in {"id_rsa", "id_ed25519", ".env"} or p.name.endswith(".env") or (p.name.startswith(".env.") and p.name != ".env.example"):
        reasons.append("private-configuration")
    if p.suffix.lower() in PRIVATE_SUFFIXES:
        reasons.append("private-export-or-key")
    if p.suffix.lower() == ".docx" and path not in PUBLIC_DOCUMENTS:
        reasons.append("personal-course-document")
    if p.name.lower().endswith((".sql.gz", ".sql.zst")):
        reasons.append("database-export")
    texts = []
    if p.suffix.lower() == ".docx":
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                for entry in archive.infolist():
                    if entry.filename.endswith(".xml"):
                        if entry.file_size > 8_000_000:
                            reasons.append("document-entry-too-large")
                            continue
                        texts.append(re.sub(r"<[^>]+>", "", archive.read(entry).decode("utf-8", errors="replace")))
        except zipfile.BadZipFile:
            reasons.append("invalid-document")
    elif b"\0" not in data[:3000] and p.suffix.lower() not in {".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico", ".woff", ".woff2"}:
        texts.append(data.decode("utf-8", errors="replace"))
    for name, pattern in PATTERNS.items():
        if any(pattern.search(text) for text in texts):
            reasons.append(name)
    if path.startswith("frontend/") and any(re.search(
            r"\bVITE_[A-Z0-9_]*(?:SECRET|TOKEN|PASSWORD|PRIVATE_KEY|API_KEY|SECURITY_CODE)\b", text) for text in texts):
        reasons.append("frontend-secret-binding")
    return sorted(set(reasons))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ref", help="Inspect a commit instead of the current index")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    command = ["git", "ls-tree", "-rz", args.ref] if args.ref else ["git", "ls-files", "--stage", "-z"]
    entries = subprocess.check_output(command, cwd=root).split(b"\0")
    failures = []
    checked = 0
    with subprocess.Popen(["git", "cat-file", "--batch"], cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE) as process:
        assert process.stdin and process.stdout
        for entry in entries:
            if not entry:
                continue
            metadata, raw_path = entry.split(b"\t", 1)
            parts = metadata.split()
            oid = parts[2] if args.ref else parts[1]
            if not args.ref and parts[2] != b"0":
                failures.append((raw_path.decode("utf-8"), ["unmerged-index"]))
                continue
            process.stdin.write(oid + b"\n")
            process.stdin.flush()
            header = process.stdout.readline().split()
            if len(header) != 3 or header[1] != b"blob":
                raise RuntimeError("Unexpected Git object; refusing incomplete scan")
            size = int(header[2])
            data = process.stdout.read(size)
            process.stdout.read(1)
            checked += 1
            path = raw_path.decode("utf-8")
            reasons = inspect(path, data)
            if reasons:
                failures.append((path, reasons))
        process.stdin.close()
        if process.wait() != 0:
            raise RuntimeError("Git content scan failed")
    for path, reasons in failures:
        print(f"BLOCKED {path}: {', '.join(reasons)}")
    print(f"Public repository check: {checked} indexed files, {len(failures)} blocked files; matched values are never printed.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
