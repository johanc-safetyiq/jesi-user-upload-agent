#!/bin/bash

# Search 1Password vault for credentials by email/username
# Usage: ./search-1password.sh "email@example.com"

# Source the environment file for the token
source /Users/johan/Work/brisbane/jesi-system/user-upload-agent/.env

if [ -z "$1" ]; then
    echo "Usage: $0 <search_term>"
    echo "Example: $0 customersolutions+elecnor@jesi.io"
    exit 1
fi

SEARCH_TERM="$1"
VAULT="Customer Support (Site Registrations)"

echo "Searching for: $SEARCH_TERM"
echo "---"

# First, try to find items by title
echo "Searching in titles..."
TITLE_MATCHES=$(op item list --vault "$VAULT" --format=json | jq -r --arg search "$SEARCH_TERM" '.[] | select(.title | ascii_downcase | contains($search | ascii_downcase)) | .id + " | " + .title')

if [ -n "$TITLE_MATCHES" ]; then
    echo "Found in titles:"
    echo "$TITLE_MATCHES"
    
    # Get details for each match
    while IFS= read -r line; do
        ITEM_ID=$(echo "$line" | cut -d' ' -f1)
        echo ""
        echo "Details for: $(echo "$line" | cut -d'|' -f2-)"
        op item get "$ITEM_ID" --vault "$VAULT" --format=json | jq -r '.fields[] | select(.label == "username" or .label == "password") | "\(.label): \(.value)"'
    done <<< "$TITLE_MATCHES"
fi

# Search for the exact email in username fields
echo ""
echo "Searching for exact username match..."

# Get all items and check username fields
ALL_ITEMS=$(op item list --vault "$VAULT" --format=json | jq -r '.[].id')

while IFS= read -r item_id; do
    USERNAME=$(op item get "$item_id" --vault "$VAULT" --format=json 2>/dev/null | jq -r '.fields[] | select(.label == "username") | .value' 2>/dev/null)
    
    if [ "$USERNAME" = "$SEARCH_TERM" ]; then
        TITLE=$(op item get "$item_id" --vault "$VAULT" --format=json | jq -r '.title')
        echo "Found exact match in: $TITLE"
        op item get "$item_id" --vault "$VAULT" --format=json | jq -r '.fields[] | select(.label == "username" or .label == "password") | "\(.label): \(.value)"'
        break
    fi
done <<< "$ALL_ITEMS"