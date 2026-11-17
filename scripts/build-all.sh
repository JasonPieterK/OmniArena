#!/usr/bin/env bash
# Builds all 24 OmniArena jars (4 platforms x 6 Minecraft versions) into dist/.
#   scripts/build-all.sh                      # everything
#   PLATFORMS="paper spigot" VERSIONS="26.2 1.21.11" scripts/build-all.sh   # a subset
# Java 25 is needed for 26.x, Java 21 for 1.21.x: set JAVA25_HOME / JAVA21_HOME (or put the right JDK on PATH
# and only build the matching versions). Purpur needs repo.purpurmc.org to be reachable.
set -uo pipefail
cd "$(dirname "$0")/.."

PLATFORMS=${PLATFORMS:-"paper purpur spigot bukkit"}
VERSIONS=${VERSIONS:-"26.3 26.2 26.1 1.21.11 1.21.10 1.21.9"}
DIST=dist
mkdir -p "$DIST"
failed=()

for ver in $VERSIONS; do
  case "$ver" in
    26.*) jh=${JAVA25_HOME:-} ;;
    *)    jh=${JAVA21_HOME:-} ;;
  esac
  for plat in $PLATFORMS; do
    echo "=== $plat / $ver ==="
    if [ -n "$jh" ]; then export JAVA_HOME="$jh"; export PATH="$jh/bin:$PATH"; fi
    if mvn -B -P"$plat,mc-$ver" clean package; then
      cp "target/OmniArena-$plat-$ver.jar" "$DIST/" || failed+=("$plat/$ver (copy)")
    else
      failed+=("$plat/$ver")
    fi
  done
done

echo
ls -1 "$DIST"
if [ ${#failed[@]} -gt 0 ]; then echo "FAILED: ${failed[*]}"; exit 1; fi
echo "All builds OK"
