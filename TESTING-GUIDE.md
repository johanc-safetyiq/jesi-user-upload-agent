# User Upload Agent - Testing Guide

This guide walks through testing each component of the User Upload Agent to validate that everything is working correctly.

## Quick Validation Checklist

Run these commands in order to validate your installation:

### 1. Configuration Check
```bash
# Verify environment is loading
clj -M -e "(require '[user-upload.config :as config]) (println \"Config loaded:\" (boolean (config/get-config)))"
```
Expected: `Config loaded: true`

### 2. Jira Connectivity
```bash
# Test Jira connection (read-only)
clj -M -e "(require '[user-upload.jira.client :as jira]) (println \"Jira connected:\" (boolean (:issues (jira/search-issues {:jql \"project = JESI\" :max-results 1}))))"
```
Expected: `Jira connected: true`

### 3. Claude CLI Check
```bash
# Check if Claude is available
clj -M -e "(require '[user-upload.ai.claude :as claude]) (let [result (claude/check-claude-availability)] (println \"Claude available:\" (:available result)))"
```
Expected: `Claude available: true` (or `false` with fallback mode)

### 4. 1Password CLI Check
```bash
# Check if 1Password CLI is available
which op && op --version
```
Expected: Path to `op` and version number

## Component-by-Component Testing

### Phase 1: Foundation Testing

#### Test Configuration Loading
```bash
cd user-upload-agent
clj -M:dev
```
```clojure
(require '[user-upload.config :as config])
(def cfg (config/get-config))
(println "Jira URL:" (get-in cfg [:jira :url]))
(println "Backend URL:" (get-in cfg [:backend :api-url]))
```

#### Test Logging
```clojure
(require '[user-upload.log :as log])
(log/info "Test info message" {:component "test" :value 123})
(log/error "Test error message" {:error "simulated"})
```

### Phase 2: External Integration Testing

#### Test Jira Operations
```bash
# 1. Search for test ticket
clj -M exploration/jira/jql-test.clj

# 2. Fetch specific issue
clj -M exploration/jira/fetch-issue.clj

# 3. Test approval detection (safe read)
clj -M exploration/jira/approval-detect.clj
```

#### Test Backend API (Mock Mode)
```bash
# 1. Test authentication
clj -M exploration/backend-api/auth-test.clj

# 2. Test fetching users/teams
clj -M exploration/backend-api/fetch-test.clj

# 3. Test user creation (mock)
clj -M exploration/backend-api/create-user.clj
```

### Phase 3: File Processing Testing

#### Test CSV Parsing
```bash
# Create a test CSV first
cat > test-users.csv << 'EOF'
Email,First Name,Last Name,Role,Teams
john.doe@example.com,John,Doe,Admin,Engineering|Support
jane.smith@example.com,Jane,Smith,User,Marketing
EOF

# Test parsing
clj -M -e "(require '[user-upload.parser.csv :as csv]) (clojure.pprint/pprint (csv/parse-csv-to-maps \"test-users.csv\"))"
```

#### Test Excel Parsing
```bash
# Test with existing Excel file
clj -M exploration/file-parsing/excel-test.clj
```

#### Test Data Validation
```bash
# Run validation tests
clj -M exploration/file-parsing/validate-rows.clj
```

#### Full Pipeline Test
```bash
# Test complete file processing pipeline
clj -M exploration/file-parsing/integration-test.clj
```

### Phase 4: AI Integration Testing

#### Test Claude Availability
```bash
clj -M exploration/ai/claude-cli-test.clj
```

#### Test Intent Detection
```bash
# Test with sample ticket data
clj -M exploration/ai/intent-prompts.clj
```

#### Test Column Mapping
```bash
# Test with various header formats
clj -M exploration/ai/column-mapping-test.clj

# Test with real-world samples
clj -M exploration/ai/mapping-samples.clj
```

### Phase 5: Workflow Testing

#### Test Tenant Extraction
```bash
clj -M exploration/workflow/tenant-extraction-test.clj
```

#### Test 1Password Integration
```bash
# First ensure you're logged into 1Password CLI
op signin

# Then test integration
clj -M exploration/workflow/onepassword-test.clj
```

#### Test Approval Workflow
```bash
# Test approval detection and fingerprinting
clj -M exploration/workflow/approval-test.clj
```

#### Full Integration Test
```bash
# Run complete workflow test
clj -M exploration/workflow/full-integration-test.clj
```

## End-to-End Testing

### Dry Run Test
Test the complete system without making changes:
```bash
# Dry run with verbose output
clj -M -m user-upload.core --dry-run --verbose --once
```

### Test with Real Ticket (JESI-5928)
```bash
# Process the test ticket
clj -M -m user-upload.core --once

# Check logs for processing details
tail -f user-upload.log  # If logging to file
```

### Watch Mode Test
```bash
# Start in watch mode with short interval for testing
clj -M -m user-upload.core --watch --interval 60 --dry-run

# In another terminal, monitor the process
ps aux | grep "user-upload.core"

# Stop with Ctrl+C
```

## Interactive REPL Testing Session

For comprehensive testing, start a REPL:
```bash
clj -M:dev
```

Then run this test sequence:
```clojure
;; 1. Load all namespaces
(require '[user-upload.config :as config])
(require '[user-upload.jira.client :as jira])
(require '[user-upload.jira.approval :as approval])
(require '[user-upload.parser.csv :as csv])
(require '[user-upload.parser.excel :as excel])
(require '[user-upload.parser.normalize :as normalize])
(require '[user-upload.parser.validate :as validate])
(require '[user-upload.ai.claude :as claude])
(require '[user-upload.ai.intent :as intent])
(require '[user-upload.ai.mapping :as mapping])
(require '[user-upload.auth.tenant :as tenant])
(require '[user-upload.workflow.orchestrator :as orchestrator])
(require '[user-upload.workflow.processor :as processor])

;; 2. Check configuration
(def cfg (config/get-config))
(println "Config loaded successfully")

;; 3. Test Jira connection
(def test-issue (jira/get-issue "JESI-5928"))
(println "Found issue:" (:key test-issue))

;; 4. Test tenant extraction
(def extracted-tenant (tenant/extract-tenant-from-ticket test-issue))
(println "Extracted tenant:" extracted-tenant)

;; 5. Test file parsing (create sample file first)
(spit "test.csv" "email,name\njohn@example.com,John Doe")
(def parsed (csv/parse-csv-to-maps "test.csv"))
(println "Parsed rows:" (count (:data parsed)))

;; 6. Test AI if available
(def claude-available (claude/check-claude-availability))
(println "Claude available:" (:available claude-available))

;; 7. Test approval check
(def approval-status (approval/check-approval-status "JESI-5928"))
(println "Approval status:" (:status approval-status))

;; 8. Clean up
(clojure.java.io/delete-file "test.csv" true)
```

## Validation Scenarios

### Scenario 1: New User Upload Request
1. Create a test CSV file with sample users
2. Attach to JESI-5928 (test ticket)
3. Run: `clj -M -m user-upload.core --once --dry-run`
4. Verify it detects the attachment and processes it

### Scenario 2: Approval Required
1. Run the system on a ticket without approval
2. Verify it posts approval request comment
3. Manually add "approved" comment
4. Run again and verify it proceeds

### Scenario 3: Invalid Data Handling
1. Create CSV with invalid emails/roles
2. Process with the system
3. Verify validation errors are reported
4. Check that valid rows still process

### Scenario 4: Multi-Tenant Processing
1. Create tickets with different tenant emails
2. Run in dry-run mode
3. Verify correct tenant extraction
4. Check 1Password integration attempts

## Performance Testing

### Memory Usage
```bash
# Monitor memory while processing
clj -M -m user-upload.core --once &
PID=$!
while kill -0 $PID 2>/dev/null; do
  ps -o pid,vsz,rss,comm -p $PID
  sleep 1
done
```

### Processing Time
```bash
# Time a full run
time clj -M -m user-upload.core --once --dry-run
```

## Troubleshooting Tests

### Test 1: Missing Configuration
```bash
# Temporarily rename .env
mv .env .env.backup
clj -M -m user-upload.core --once
# Should show clear error about missing configuration
mv .env.backup .env
```

### Test 2: Invalid Jira Credentials
```bash
# Temporarily modify .env with wrong token
# Run and verify clear error message
clj -M -m user-upload.core --once
```

### Test 3: Network Issues
```bash
# Test with network disabled (airplane mode)
# Should show connection errors gracefully
```

## Success Criteria

Your installation is working correctly if:

1. ✅ Configuration loads from .env automatically
2. ✅ Can connect to Jira and fetch tickets
3. ✅ Can parse CSV and Excel files
4. ✅ Data validation catches invalid entries
5. ✅ AI integration works (or falls back gracefully)
6. ✅ Tenant extraction identifies correct tenant
7. ✅ 1Password CLI can be invoked (when available)
8. ✅ Approval workflow detects comments correctly
9. ✅ Dry-run mode shows what would happen
10. ✅ Watch mode polls continuously

## Next Steps

Once all tests pass:
1. Remove `--dry-run` flag for actual processing
2. Set up proper JQL for your tickets
3. Configure watch mode interval for production
4. Monitor logs for any issues
5. Set up alerts for failures

## Support

If any test fails:
1. Check the specific error message
2. Verify prerequisites (Java, Clojure, 1Password CLI)
3. Check .env configuration
4. Review exploration test output
5. Check changelog.md for implementation details