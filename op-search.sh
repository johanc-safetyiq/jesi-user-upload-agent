#!/bin/bash

# Fast 1Password search for credentials
# Usage: ./op-search.sh "search_term"

source /Users/johan/Work/brisbane/jesi-system/user-upload-agent/.env

if [ -z "$1" ]; then
    echo "Usage: $0 <search_term>"
    echo "Example: $0 elecnor"
    exit 1
fi

SEARCH="$1"
VAULT="Customer Support (Site Registrations)"

echo "Searching for: $SEARCH"
echo "---"

# Search in item titles (fast)
MATCHES=$(op item list --vault "$VAULT" | grep -i "$SEARCH")

if [ -n "$MATCHES" ]; then
    echo "Found matches:"
    echo "$MATCHES"
    echo ""
    
    # Extract first item ID and get details
    ITEM_ID=$(echo "$MATCHES" | head -1 | awk '{print $1}')
    
    if [ -n "$ITEM_ID" ]; then
        echo "Getting credentials for first match:"
        op item get "$ITEM_ID" --vault "$VAULT" --format=json | jq -r '.fields[] | select(.label == "username" or .label == "password") | "\(.label): \(.value)"'
    fi
else
    echo "No matches found for: $SEARCH"
fi