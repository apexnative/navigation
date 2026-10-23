#!/bin/bash
# The quick checks in a row: TypeScript, lint, Jest, copyright headers, the Claude hooks, the native contract's generated files, the docs'
# API reference. One line each.
DIR="$(dirname "$0")"
source "$DIR/lib/quiet.sh"
failed=0
"$DIR/typecheck.sh" || failed=1
"$DIR/lint.sh" || failed=1
"$DIR/test-js.sh" || failed=1
quiet headers tail node scripts/check-license-headers.mjs || failed=1
quiet hooks tail node --test scripts/claude-hooks/pre-edit.test.mjs || failed=1
quiet contract tail node scripts/contract.mjs --check || failed=1
"$DIR/docs.sh" api || failed=1
exit $failed
