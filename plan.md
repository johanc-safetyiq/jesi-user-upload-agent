# User Upload Agent Implementation Plan

## Overview
Step-by-step implementation plan for a Clojure-based user upload agent that automates user imports from Jira tickets. Each step is small, independently testable, and commits upon completion.

## Project Structure
```
user-upload-agent/
├── deps.edn              # Dependencies and aliases
├── src/                  # Production code only
│   └── user_upload/      # Main namespace
├── exploration/          # Discovery scripts and tests
│   ├── jira/            # Jira API exploration
│   ├── backend-api/     # Backend API tests
│   ├── ai/              # Claude Code CLI tests
│   └── file-parsing/    # CSV/Excel parsing tests
├── resources/           # Config files and templates
├── test/               # Unit tests
└── dev/                # Development utilities
```

## Phase 1: Foundation & Local Development Setup

### Step 1: Project Scaffolding
**Research (exploration/):**
- Create `exploration/setup/test-deps.clj` to validate deps.edn setup
- Test REPL connection and basic namespace loading

**Implementation:**
- Create deps.edn with core dependencies (clojure, tools.logging)
- Set up src/user_upload/core.clj with main entry point
- Add dev alias for REPL with extra paths
- Create .gitignore for Clojure projects

**Validation:**
- `clj -M:dev` starts REPL successfully
- `(require 'user-upload.core)` works
- Can call a basic hello-world function

**Commit:** `git commit -m "Initial project structure with deps.edn"`

---

### Step 2: Configuration Management
**Research (exploration/):**
- Create `exploration/config/env-test.clj` to test environ library
- Write script to validate config loading from .env.example

**Implementation:**
- Add environ and aero dependencies to deps.edn
- Create src/user_upload/config.clj
- Create resources/config.edn with environment placeholders
- Create .env.example with all required variables
- Document local setup in README

**Validation:**
- Config loads from .env file locally
- Can access config values from REPL
- Missing required config shows helpful error

**Commit:** `git commit -m "Add configuration management with environ"`

---

### Step 3: Simple Logging
**Research (exploration/):**
- Create `exploration/logging/compare-libs.clj` to test timbre vs tools.logging
- Test JSON output format for structured logging

**Implementation:**
- Add chosen logging library to deps.edn
- Create src/user_upload/log.clj with simple wrappers
- Configure JSON output format
- Add logging to core namespace

**Validation:**
- Logs output readable JSON
- Different log levels work (info, warn, error)
- Can see logs in REPL during development

**Commit:** `git commit -m "Add JSON logging infrastructure"`

---

## Phase 2: External Integrations

### Step 4: Jira REST API Client - Basic Operations
**Research (exploration/):**
- Create `exploration/jira/auth-test.clj` to test Basic auth with PAT
- Write `exploration/jira/jql-test.clj` to validate JQL queries
- Test issue fetching in `exploration/jira/fetch-issue.clj`

**Implementation:**
- Add clj-http to deps.edn
- Create src/user_upload/jira/client.clj
- Implement authentication headers
- Add JQL query function
- Create issue fetching with attachments metadata

**Validation:**
- Can query Jira with test JQL from REPL
- Fetches issue with all fields
- Returns attachment metadata

**Commit:** `git commit -m "Add Jira REST API client with basic operations"`

---

### Step 5: Jira Comment & Transition Management
**Research (exploration/):**
- Create `exploration/jira/comment-test.clj` for comment operations
- Write `exploration/jira/transition-test.clj` for status changes
- Test approval detection logic in `exploration/jira/approval-detect.clj`

**Implementation:**
- Extend src/user_upload/jira/client.clj with comment functions
- Add transition operations
- Create src/user_upload/jira/approval.clj for approval detection
- Implement structured comment parsing

**Validation:**
- Can post a test comment to Jira
- Can transition issue Open -> Review
- Correctly detects "approved" comments after bot request

**Commit:** `git commit -m "Add Jira comment and transition management"`

---

### Step 6: Backend API Client
**Research (exploration/):**
- Create `exploration/backend-api/auth-test.clj` for login flow
- Write `exploration/backend-api/fetch-test.clj` for GET operations
- Test user creation in `exploration/backend-api/create-user.clj`

**Implementation:**
- Create src/user_upload/api/client.clj
- Implement auth with token storage
- Add fetch operations (users, teams, roles)
- Create user and team creation functions
- Handle v1 vs v2 endpoint routing

**Validation:**
- Gets auth token successfully
- Fetches existing users/teams/roles
- Can create a test team
- Token persists across requests

**Commit:** `git commit -m "Add backend API client with auth and CRUD operations"`

---

## Phase 3: File Processing

### Step 7: Excel/CSV Parser
**Research (exploration/):**
- Create `exploration/file-parsing/excel-test.clj` using docjure
- Write `exploration/file-parsing/csv-test.clj` for CSV parsing
- Test multi-sheet handling in `exploration/file-parsing/multi-sheet.clj`

**Implementation:**
- Add dk.ative/docjure and data.csv to deps.edn
- Create src/user_upload/parser/excel.clj
- Create src/user_upload/parser/csv.clj
- Add multi-sheet support
- Handle encoding detection

**Validation:**
- Parses test Excel file from exploration/
- Reads CSV with different encodings
- Extracts all sheets from multi-sheet file

**Commit:** `git commit -m "Add Excel and CSV file parsing"`

---

### Step 8: Header Normalization & Validation
**Research (exploration/):**
- Create `exploration/file-parsing/normalize-test.clj` for header mapping
- Write validation rules test in `exploration/file-parsing/validate-rows.clj`

**Implementation:**
- Create src/user_upload/parser/normalize.clj
- Add header normalization (lowercase, trim, dedupe)
- Create src/user_upload/parser/validate.clj
- Implement required field checking
- Add email validation and role checking

**Validation:**
- Headers normalize to expected format
- Detects missing required columns
- Validates emails and roles correctly

**Commit:** `git commit -m "Add data normalization and validation"`

---

## Phase 4: AI Integration

### Step 9: Claude Code CLI Integration
**Research (exploration/):**
- Create `exploration/ai/claude-cli-test.clj` to test CLI invocation
- Write `exploration/ai/json-parse-test.clj` for response parsing
- Test timeout handling in `exploration/ai/timeout-test.clj`

**Implementation:**
- Create src/user_upload/ai/claude.clj
- Implement process spawning with babashka.process
- Add JSON response parsing
- Create timeout wrapper
- Add environment variable for API key

**Validation:**
- Claude Code CLI runs locally with API key
- Parses JSON responses correctly
- Handles timeouts gracefully (30s default)

**Commit:** `git commit -m "Add Claude Code CLI integration"`

---

### Step 10: Intent Detection & Column Mapping
**Research (exploration/):**
- Create `exploration/ai/intent-prompts.clj` to test prompts
- Write `exploration/ai/column-mapping-test.clj` for mapping accuracy
- Test various file headers in `exploration/ai/mapping-samples.clj`

**Implementation:**
- Create src/user_upload/ai/intent.clj for intent detection
- Create src/user_upload/ai/mapping.clj for column mapping
- Add prompt templates to resources/prompts/
- Implement fallback for AI failures

**Validation:**
- Intent detection works on sample tickets
- Column mapping handles common variations
- Falls back gracefully when AI unavailable

**Commit:** `git commit -m "Add AI-powered intent detection and column mapping"`

---

## Phase 5: Core Workflow

### Step 11: Ticket Processing Loop with Tenant Authentication
**Research (exploration/):**
- Create `exploration/workflow/jql-builder.clj` to test query generation
- Write `exploration/workflow/ticket-filter.clj` for status filtering
- Create `exploration/workflow/tenant-extract.clj` to test tenant name extraction
- Write `exploration/workflow/onepassword-test.clj` to test 1Password CLI integration
- Test error isolation in `exploration/workflow/error-handling.clj`

**Implementation:**
- Create src/user_upload/workflow/processor.clj
- Build JQL from config keywords
- Implement ticket fetching and filtering
- Create src/user_upload/workflow/tenant.clj for tenant extraction
  - Parse tenant name from ticket title/description
  - Extract customersolutions+<tenant>@jesi.io pattern
- Create src/user_upload/auth/onepassword.clj
  - Integrate 1Password CLI using babashka.process
  - Fetch password for tenant service account
  - Cache credentials per tenant during runtime
- Add per-ticket authentication with correct tenant
- Add status checking (Open vs Review)
- Create error boundaries per ticket

**Validation:**
- Generates correct JQL from keywords
- Correctly extracts tenant name from tickets
- Successfully retrieves password from 1Password
- Authenticates with correct tenant credentials
- Fetches only assigned Open/Review tickets
- Continues processing after single ticket error

**Commit:** `git commit -m "Add ticket processing loop with tenant authentication"`

---

### Step 12: Approval Flow
**Research (exploration/):**
- Create `exploration/workflow/approval-test.clj` for detection logic
- Write `exploration/workflow/sha256-test.clj` for fingerprinting
- Test comment generation in `exploration/workflow/comment-format.clj`

**Implementation:**
- Create src/user_upload/workflow/approval.clj
- Add SHA-256 fingerprinting for attachments
- Generate structured approval request comments
- Implement approval detection from comments
- Add approval invalidation on file change

**Validation:**
- Creates readable approval request with CSV
- Detects "approved" comment after bot request
- Invalidates approval if attachment changes

**Commit:** `git commit -m "Add approval flow with fingerprinting"`

---

### Step 13: User Upload Pipeline
**Research (exploration/):**
- Create `exploration/upload/team-creation.clj` for team logic
- Write `exploration/upload/user-batch.clj` to test strategies
- Test error aggregation in `exploration/upload/error-collect.clj`

**Implementation:**
- Create src/user_upload/upload/orchestrator.clj
- Implement pre-upload checks (fetch existing data)
- Add team creation for missing teams
- Create per-user upload with continuation on error
- Aggregate results (created/existed/failed)

**Validation:**
- Creates missing teams before users
- Continues after individual user failures
- Reports accurate counts

**Commit:** `git commit -m "Add user upload pipeline with team creation"`

---

## Phase 6: Reporting & Error Handling

### Step 14: Result Reporting
**Research (exploration/):**
- Create `exploration/reporting/csv-gen.clj` for failure CSV
- Write `exploration/reporting/comment-struct.clj` for JSON formatting
- Test attachment upload in `exploration/reporting/attach-test.clj`

**Implementation:**
- Create src/user_upload/reporting/generator.clj
- Generate failure CSV with all error details
- Create structured JSON comments for Jira
- Add proposed users CSV generation
- Implement final report with counts

**Validation:**
- Failure CSV contains all required columns
- JSON comments are machine-parseable
- Reports show created/existed/failed counts

**Commit:** `git commit -m "Add comprehensive result reporting"`

---

### Step 15: Error Handling & Recovery
**Research (exploration/):**
- Create `exploration/errors/partial-fail.clj` for failure scenarios
- Write `exploration/errors/retry-logic.clj` for API failures
- Test logging context in `exploration/errors/log-context.clj`

**Implementation:**
- Create src/user_upload/errors/handler.clj
- Add try-catch wrapping at all levels
- Implement detailed error context logging
- Create graceful degradation for AI failures
- Add continuation after partial failures

**Validation:**
- Single user failure doesn't stop batch
- API timeouts are logged with context
- System continues without AI if needed

**Commit:** `git commit -m "Add robust error handling and recovery"`

---

## Phase 7: Integration & Deployment

### Step 16: Main Entry Point & CLI
**Research (exploration/):**
- Create `exploration/main/cli-args.clj` for argument parsing
- Write `exploration/main/scheduler.clj` for run loop testing

**Implementation:**
- Create src/user_upload/main.clj with -main function
- Add command-line argument parsing
- Wire together all components
- Create run-once and daemon modes
- Add graceful shutdown handling

**Validation:**
- Can run with `clj -M:run`
- Processes all tickets in one pass
- Shuts down cleanly on SIGTERM

**Commit:** `git commit -m "Add main entry point and CLI"`

---

### Step 17: End-to-End Testing
**Research (exploration/):**
- Create `exploration/e2e/full-flow.clj` for complete workflow
- Write test data generators in `exploration/e2e/test-data.clj`
- Create mock Jira ticket in test environment

**Implementation:**
- Create test namespace with full workflow
- Add sample Excel/CSV files in resources/test/
- Write end-to-end test scenarios
- Document manual testing procedure
- Create test checklist

**Validation:**
- Full flow works with test ticket
- Handles approval flow correctly
- Creates users successfully

**Commit:** `git commit -m "Add end-to-end testing suite"`

---

### Step 18: Docker Container (Deployment Ready)
**Research (exploration/):**
- Create `exploration/docker/build-test.sh` for container testing
- Test Claude Code CLI installation in container

**Implementation:**
- Create Dockerfile with Clojure base image
- Add deps.edn and source code
- Install Claude Code CLI via npm
- Set up environment variables
- Create docker-compose.yml for local testing

**Validation:**
- Container builds without errors
- Runs locally with docker-compose
- Claude Code CLI works in container

**Commit:** `git commit -m "Add Docker containerization"`

---

### Step 19: Azure Deployment Configuration
**Research (exploration/):**
- Create `exploration/azure/deploy-test.sh` for deployment testing
- Document Azure Job configuration requirements

**Implementation:**
- Create deployment/azure/ directory
- Add container registry push script
- Document Azure Job YAML configuration
- Create environment-specific configs
- Add deployment README

**Validation:**
- Container pushes to registry
- Documentation covers all Azure setup
- Config works for dev/staging/prod

**Commit:** `git commit -m "Add Azure deployment configuration"`

---

## Success Criteria

Each step must:
1. Be completed in 1-2 days maximum
2. Include exploration scripts that validate approach
3. Build on previous steps incrementally
4. Commit code once validated
5. Update changelog.md with results
6. Leave the system in a working state

## Coordination Guidelines

### For Engineers
- Start each step with exploration scripts
- Keep exploration code separate from src/
- Commit immediately after validation
- Update changelog with findings and blockers
- Communicate blockers early

### Daily Workflow
1. Pick next unstarted step
2. Run exploration scripts first
3. Implement based on findings
4. Validate locally
5. Commit with descriptive message
6. Update changelog
7. Move to next step or hand off

## Key Decisions Made

- **deps.edn** for dependency management (not Leiningen)
- **Local development first** - Docker only for deployment
- **Simple config** - .env files locally, environment variables in production
- **Exploration folder** - Keep src/ clean, test ideas separately
- **Commit frequently** - After each validated step
- **Pragmatic approach** - Working system over perfect system

## Risk Mitigation

- **AI Failures:** System continues without AI (manual header mapping)
- **API Timeouts:** Log and continue with next ticket
- **Large Files:** Process in chunks if needed
- **Missing Config:** Clear error messages on startup
- **Jira Access:** Test connectivity before processing

## Testing Approach

- Exploration scripts validate each integration
- Manual testing via REPL during development
- End-to-end test with real Jira ticket
- Document test scenarios for handoff

## File Organization

```
src/user_upload/
├── core.clj                 # Entry point
├── config.clj              # Configuration
├── log.clj                 # Logging utilities
├── jira/
│   ├── client.clj         # Jira API
│   └── approval.clj       # Approval detection
├── api/
│   └── client.clj         # Backend API
├── parser/
│   ├── excel.clj          # Excel parsing
│   ├── csv.clj            # CSV parsing
│   ├── normalize.clj      # Header normalization
│   └── validate.clj       # Data validation
├── ai/
│   ├── claude.clj         # Claude CLI wrapper
│   ├── intent.clj         # Intent detection
│   └── mapping.clj        # Column mapping
├── workflow/
│   ├── processor.clj      # Main loop
│   └── approval.clj       # Approval flow
├── upload/
│   └── orchestrator.clj   # Upload pipeline
├── reporting/
│   └── generator.clj      # Reports & CSVs
├── errors/
│   └── handler.clj        # Error handling
└── main.clj               # CLI entry point
```