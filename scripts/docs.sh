#!/bin/bash
# The docs site: `api` (default) builds the API reference and fails when docs/sections.json and the exports differ,
# and says how many exports have no JSDoc; `typecheck` and `build` check the site. Prints OK, or why it failed.
# Usage: scripts/docs.sh [api | typecheck | build]
source "$(dirname "$0")/lib/quiet.sh"
case ${1:-api} in
  api)
    quiet docs-api tail node docs/scripts/api.mjs || exit 1
    # The exports without JSDoc, by count; the list is in the log.
    sed -n 's/^\[api\] without JSDoc: \(.*\)/  \1 exports without JSDoc: .logs\/docs-api.log/p' "$LOGS/docs-api.log"
    ;;
  typecheck) quiet docs-typecheck tsc yarn --cwd docs typecheck ;;
  build) quiet docs-build tail yarn --cwd docs build ;;
  *) echo "Usage: scripts/docs.sh [api | typecheck | build]" && exit 1 ;;
esac
