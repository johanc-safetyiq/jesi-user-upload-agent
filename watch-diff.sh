#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-.}"
ROOT="$(cd "$ROOT" && pwd)"

# Temp snapshot dir; cleaned up on exit
SNAP="$(mktemp -d)"
cleanup() { rm -rf "$SNAP"; }
trap cleanup EXIT

# Define exclusions
EXCLUDES=(
  '.git'
  '.clj-kondo'
  '.lsp'
  '.cpcache'
  '.nrepl-port'
  '.portal'
  '.claude'
  'node_modules'
  'target'
  'dist'
  'build'
  '.cache'
  '.idea'
  '.vscode'
  '__pycache__'
  '*.pyc'
  '.DS_Store'
  'Thumbs.db'
  '*.swp'
  '*.swo'
  '*~'
  '.#*'
  '#*#'
)

# Build rsync exclusions
RSYNC_EXCLUDES=()
for pattern in "${EXCLUDES[@]}"; do
  RSYNC_EXCLUDES+=(--exclude "$pattern")
done

# Initial snapshot
rsync -a --delete "${RSYNC_EXCLUDES[@]}" "$ROOT"/ "$SNAP"/

clear
echo "Watching: $ROOT"
echo "Press Ctrl-C to stop."
echo ""

# Build fswatch exclusions
FSWATCH_EXCLUDES=()
for pattern in "${EXCLUDES[@]}"; do
  FSWATCH_EXCLUDES+=(--exclude "$pattern")
done

# Use fswatch with proper flags
# -r: recursive
# -x: extended format (shows event types)
# -L: follow symlinks
fswatch -r "${FSWATCH_EXCLUDES[@]}" "$ROOT" | while IFS= read -r event; do
  # Skip empty events
  [[ -z "$event" ]] && continue
  
  # Extract filename from event (fswatch outputs: filename event_flags)
  filename="${event%% *}"
  
  # Skip if file doesn't exist or is in excluded paths
  [[ ! -e "$filename" ]] && continue
  
  clear
  echo "=== $(date) ==="
  echo "Changed: ${filename#$ROOT/}"
  echo ""
  
  # Show diff between snapshot and current tree
  git --no-pager diff --no-index --color=always -- "$SNAP" "$ROOT" 2>/dev/null || true
  
  # Refresh snapshot for next change
  rsync -a --delete "${RSYNC_EXCLUDES[@]}" "$ROOT"/ "$SNAP"/
done