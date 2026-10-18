#!/bin/sh
# loadtest/reset.sh — remove the load-test dataset only, leaving the demo user and everything else
# untouched. This is a thin wrapper around reset.sql for convenience; run.sh uses the same SQL.
#
# Usage:
#   ./loadtest/reset.sh

set -eu
exec docker compose --profile loadtest run --rm seed /sql/reset.sql
