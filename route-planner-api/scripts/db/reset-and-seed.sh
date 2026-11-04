#!/usr/bin/env sh
# Empties the local Docker database and fills it with demo data.
# Run from the project root:  sh scripts/db/reset-and-seed.sh
set -e

cd "$(dirname "$0")/../.."

run_sql() {
    # ON_ERROR_STOP: stop at the first error instead of carrying on with half the data
    docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "$1"
}

run_sql scripts/db/reset-data.sql
run_sql scripts/db/seed-data.sql
echo "Database reset and seeded. Every user's password is Password123."
