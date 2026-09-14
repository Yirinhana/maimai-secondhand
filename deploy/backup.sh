#!/usr/bin/env bash
# Root-only local backup. Retains existing backups; no automatic deletion.
set -euo pipefail
umask 077
test "$(id -u)" = 0
exec 9>/run/lock/maimai-backup.lock
flock -n 9
free_kb=$(df -Pk /var/backups | awk 'NR==2 {print $4}')
test "$free_kb" -gt 2097152
backup="/var/backups/maimai/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -m 0700 "$backup"
/opt/maimai/runtime/mysql/usr/bin/mysqldump --defaults-file=/etc/maimai/mysql-root.cnf \
  --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF maimai \
  | gzip > "$backup/database.sql.gz"
tar -C /var/lib/maimai -czf "$backup/images.tar.gz" \
  uploads avatars private-message-images private-aftersale-images
cp /opt/maimai/current/VERSION "$backup/VERSION"
cd "$backup"
sha256sum database.sql.gz images.tar.gz VERSION > SHA256SUMS
touch COMPLETE
echo "BACKUP_OK $backup"
