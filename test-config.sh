#!/bin/bash

# Test configuration loading with environment variables
echo "=== Testing Configuration Loading ==="

# Source the test environment file
set -a
source .env.test
set +a

# Test basic config loading
echo "1. Testing basic config loading..."
clj -M:dev -e "(do (require 'user-upload.config) (let [cfg (user-upload.config/get-config)] (println \"✓ Config loaded successfully:\") (clojure.pprint/pprint (select-keys cfg [:jira :api :ai]))))"

echo ""
echo "2. Testing config validation error (empty required field)..."
# Temporarily unset a required variable to test validation error
unset JIRA_EMAIL
clj -M:dev -e "(do (require 'user-upload.config) (user-upload.config/reset-config!) (try (user-upload.config/get-config) (println \"✗ Should have failed\") (catch Exception e (if (re-find #\"Missing required configuration keys\" (.getMessage e)) (println \"✓ Config validation error handled correctly\") (println \"✗ Unexpected error:\" (.getMessage e))))))"

echo ""
echo "=== Configuration Tests Complete ==="