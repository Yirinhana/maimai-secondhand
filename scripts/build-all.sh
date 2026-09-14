#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(tr -d '\r\n' < "$ROOT/VERSION")"
cd "$ROOT"
mvn -B -ntp "-Drevision=$VERSION" -f backend/pom.xml verify
cd frontend
npm ci
npm run typecheck
npm run build
