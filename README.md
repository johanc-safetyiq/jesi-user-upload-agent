# User Upload Agent

An automated Clojure-based system for processing user import requests from Jira tickets, with intelligent file parsing, tenant-aware authentication, and approval workflows.

## Overview

The User Upload Agent monitors Jira tickets for user import requests, automatically processes CSV/Excel attachments, validates data, obtains necessary approvals, and creates users in the JESI backend system. It features AI-assisted column mapping, multi-tenant authentication via 1Password, and comprehensive error handling.

## Prerequisites

- **Clojure CLI** (1.11+)
- **Java** (11+)
- **1Password CLI** (`op`) - Authenticated and configured
- **Claude CLI** (`claude`) - Logged in and authenticated (REQUIRED for complex files)
- **Jira Access** - API token and permissions to read/comment on tickets
- **Backend API Access** - Credentials stored in 1Password

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd user-upload-agent
```

2. Copy and configure the environment file:
```bash
cp .env.example .env
# Edit .env with your Jira credentials and API endpoints
```

3. Verify dependencies:
```bash
clj -M:dev
# In REPL, test configuration:
(require '[user-upload.config :as config])
(config/get-config)
```

## Configuration

### Environment Variables (.env)

```bash
# Jira Configuration
JIRA_URL=https://your-domain.atlassian.net
JIRA_USERNAME=your-email@company.com
JIRA_API_TOKEN=your-jira-api-token
JIRA_JQL=project = JESI AND labels = user-upload AND status in (Open, Review)

# Backend API Configuration
BASE_API_URL=https://api.jesi.io/application
BASE_CLJ_API_URL=https://apiv2.jesi.io

# 1Password Configuration
OP_SERVICE_ACCOUNT_TOKEN=your-1password-service-account-token
# OP_VAULT_NAME=JESI Customer Solutions  # Optional, defaults to this

# Logging
LOG_LEVEL=INFO
```

### Tenant Authentication

The system uses tenant-specific service accounts stored in 1Password:
- Pattern: `customersolutions+<tenant>@jesi.io`
- 1Password entry: `<tenant>-service-account`
- Credentials are fetched automatically based on ticket content

## Usage

### Main CLI Commands

No need to source .env - the application automatically loads it!

#### Command Line Options
```
Options:
  -h, --help                     Show help message
  -v, --verbose                  Enable verbose logging
  -d, --dry-run                  Perform a dry run without making changes
  -c, --config CONFIG            Configuration file path (default: resources/config.edn)
  -o, --once              true   Run once and exit (default behavior)
  -w, --watch             false  Watch mode - continuously poll for new tickets
  -s, --single-ticket     false  Process only the first ticket found (useful for testing)
  -t, --ticket KEY        nil    Process a specific ticket by key (e.g., JESI-7749)
  -i, --interval SECONDS  300    Polling interval in seconds for watch mode
```

#### Process All Open Tickets (Default)
Process all Open and Review tickets, handling each appropriately:
```bash
clj -M -m user-upload.core
# or explicitly:
clj -M -m user-upload.core --once
```

#### Process Specific Ticket
Process a single ticket by its key:
```bash
clj -M -m user-upload.core --ticket JESI-7749
```

#### Process First Ticket Only
Process only the first ticket from the JQL results (useful for testing):
```bash
clj -M -m user-upload.core --single-ticket
```

#### Watch Mode
Continuously monitor for new tickets:
```bash
clj -M -m user-upload.core --watch
# With custom interval (default is 300 seconds):
clj -M -m user-upload.core --watch --interval 60
```

#### Dry Run
See what would happen without making changes:
```bash
clj -M -m user-upload.core --dry-run
# With verbose output:
clj -M -m user-upload.core --dry-run --verbose
# For a specific ticket:
clj -M -m user-upload.core --ticket JESI-7749 --dry-run
```

#### Help
View all available options:
```bash
clj -M -m user-upload.core --help
```

### Ticket Processing Behavior

The agent handles tickets based on their status:

- **Open tickets**: Full processing - download attachments, parse, validate, request approval if needed
  - If 1Password credentials missing: Comment with setup instructions and transition to "Info Required"
- **Info Required tickets**: Skipped - waiting for credentials to be configured
- **Review tickets with approval**: Process the upload and transition to Done
- **Review tickets pending approval**: Skip with "waiting for approval" message
- **Review tickets without approval request**: Skip with warning (unexpected state)

Example output:
```
Processed 5 tickets: 2 successful, 1 failed, 2 skipped
```

### Testing Individual Components

#### 1. Test Jira Integration
```bash
# Search for tickets
clj -M -e "(require '[user-upload.jira.client :as jira]) (jira/search-issues {:jql \"project = JESI AND key = JESI-5928\" :max-results 1})"

# Fetch specific issue
clj -M -e "(require '[user-upload.jira.client :as jira]) (jira/get-issue \"JESI-5928\")"

# Check approval status
clj -M -e "(require '[user-upload.jira.approval :as approval]) (approval/check-approval-status \"JESI-5928\")"
```

#### 2. Test File Processing
```bash
# Parse CSV file
clj -M exploration/file-parsing/csv-test.clj

# Parse Excel file  
clj -M exploration/file-parsing/excel-test.clj

# Test normalization
clj -M exploration/file-parsing/normalize-test.clj

# Test validation
clj -M exploration/file-parsing/validate-rows.clj

# Full pipeline test
clj -M exploration/file-parsing/integration-test.clj
```

#### 3. Test AI Integration
```bash
# Check Claude availability
clj -M -e "(require '[user-upload.ai.claude :as claude]) (claude/check-claude-availability)"

# Test intent detection
clj -M exploration/ai/intent-prompts.clj

# Test column mapping
clj -M exploration/ai/column-mapping-test.clj

# Test with real samples
clj -M exploration/ai/mapping-samples.clj
```

#### 4. Test Backend API (Mock Mode)
```bash
# Test authentication
clj -M exploration/backend-api/auth-test.clj

# Test user fetching
clj -M exploration/backend-api/fetch-test.clj

# Test user creation (mock)
clj -M exploration/backend-api/create-user.clj
```

#### 5. Test Workflow Components
```bash
# Test tenant extraction
clj -M exploration/workflow/tenant-extraction-test.clj

# Test 1Password integration
clj -M exploration/workflow/onepassword-test.clj

# Test approval workflow
clj -M exploration/workflow/approval-test.clj

# Full integration test
clj -M exploration/workflow/full-integration-test.clj
```

### Interactive REPL Testing

Start a REPL session for interactive testing:
```bash
clj -M:dev
```

Then run exploration tests:
```clojure
;; Load and run Jira tests
(load-file "exploration/jira/approval-detect.clj")
(in-ns 'exploration.jira.approval-detect)
(run-all-tests "JESI-5928")

;; Load and run file parsing tests
(load-file "exploration/file-parsing/integration-test.clj")
(in-ns 'exploration.file-parsing.integration-test)
(run-integration-tests)

;; Load and run AI tests
(load-file "exploration/ai/claude-cli-test.clj")
(in-ns 'exploration.ai.claude-cli-test)
(run-all-claude-tests)

;; Load and run workflow tests
(load-file "exploration/workflow/full-integration-test.clj")
(in-ns 'exploration.workflow.full-integration-test)
(run-integration-test)
```

## Test Environment

### Safe Testing Ticket
- **JESI-5928**: Use this ticket for all write operations (comments, transitions, uploads)
- **Production tickets**: Read-only access for safety

### Sample Files
The `exploration/file-parsing/samples/` directory contains test CSV files:
- `sample-users.csv` - Valid user data
- `sample-with-issues.csv` - Data with validation errors
- `sample-unicode.csv` - Unicode/international characters
- `sample-windows.csv` - Windows-1252 encoding

## Processing Workflow

### For Open Tickets
1. **Ticket Discovery**: Searches Jira using configured JQL (status IN ["Open", "Review", "Info Required"])
2. **Intent Detection**: AI determines if ticket is a user upload request
3. **Tenant Extraction**: Identifies tenant from ticket content (e.g., customersolutions+qbirt@jesi.io)
4. **Authentication**: Fetches tenant credentials from 1Password CLI
   - If credentials not found: Posts comment with setup instructions and transitions to "Info Required"
   - If credentials found: Continues with processing
5. **Attachment Processing**: Downloads and parses CSV/Excel files
6. **Sheet Detection**: For complex Excel files, AI identifies the correct sheet and data location
7. **Column Mapping**: AI-assisted mapping of file headers to expected schema
8. **Data Validation**: Validates emails, roles, teams, handles duplicates
9. **Approval Gate**: If AI/transformations were used, generates CSV and requests approval
10. **Ticket Transition**: Moves ticket to Review status for approval

### For Review Tickets
1. **Approval Check**: Verifies if "approved" comment exists after bot request
2. **If Approved**: 
   - Re-authenticates with tenant credentials (transitions to "Info Required" if missing)
   - Validates attachment fingerprints haven't changed
   - Creates missing teams in backend
   - Uploads users individually (continues on failures)
   - Posts final report with results
   - Transitions ticket to Done/Closed
3. **If Pending**: Skips ticket with "waiting for approval" message
4. **If No Request**: Logs warning (unexpected state)

### For Info Required Tickets
1. **Skip Processing**: These tickets are awaiting manual intervention
2. **Manual Action Required**: 
   - Create 1Password entry for the tenant
   - Transition ticket back to "Open" status
3. **Next Run**: Agent will retry processing when ticket is back in "Open" status

## File Processing Capabilities

### Simple Mode (No AI Required)
- CSV files with exact column names: `email`, `first name`, `last name`, `job title`, `mobile number`, `teams`, `user role`
- Excel files where headers are in the first row of the first sheet with exact column matches

### Complex Mode (AI Required)
- Multi-sheet Excel files where user data is not in the first sheet
- Files with instruction rows before the actual headers
- Headers not in the first row (e.g., row 4 or 5)
- Column names that don't exactly match but are semantically similar
- Automatic detection of sheet structure and data location

### Example: JESI-7754
The system can handle complex files like "Copy of User Team - Qbirt.xlsx" where:
- Sheet 1 ("Team") contains configuration data
- Sheet 2 ("User") contains the actual user data
- Rows 1-3 contain instructions
- Row 4 contains headers
- Row 5+ contains user data

## Special Status Transitions

### Info Required Status
The agent automatically transitions tickets to "Info Required" when:
- **Missing 1Password Credentials**: No entry found for the tenant's service account
- **Authentication Failures**: Credentials exist but authentication fails

The bot posts a detailed comment explaining:
- The missing tenant and expected email format
- Instructions for creating the 1Password entry
- Next steps to resume processing

Once credentials are configured, manually transition the ticket back to "Open" status.

## Approval Message Format

When approval is required, the bot posts a structured comment in Jira containing:

- **Tenant Email**: The service account that will be used (e.g., customersolutions+qbirt@jesi.io)
- **1Password Status**: ✓ Found or ✗ Not found for credentials
- **Summary**: User count, team count, attachment count
- **User Preview**: First 10 users to be created
- **Teams**: List of all teams involved
- **Column Mapping**: Shows how file columns were mapped to expected fields
- **CSV Attachment**: Clean CSV file with validated data for review

To approve, reply with exactly "approved" (case-insensitive).

## Features

### Core Capabilities
- ✅ Multi-tenant authentication via 1Password
- ✅ CSV and Excel file parsing with encoding detection
- ✅ AI-powered column mapping with Claude
- ✅ Comprehensive data validation
- ✅ Approval workflow with SHA-256 fingerprinting
- ✅ Automatic team creation
- ✅ Batch processing with error isolation
- ✅ Watch mode for continuous monitoring

### Data Validation Rules
- Email format and uniqueness checking
- Role validation against allowed values
- Team existence verification
- Required field validation
- Mobile number defaults to "0" when empty

### Error Handling
- Individual ticket failures don't stop processing
- User creation failures don't stop batch
- Graceful degradation when AI unavailable
- Comprehensive error reporting

## Troubleshooting

### Common Issues

#### 1Password CLI Not Found
```bash
# Install 1Password CLI
brew install --cask 1password-cli

# Authenticate
op signin
```

#### Claude CLI Not Available
The system will fall back to heuristic methods for intent detection and column mapping.

#### Jira Connection Issues
Check your `.env` file for correct:
- `JIRA_URL` (no trailing slash)
- `JIRA_EMAIL` (your Atlassian account email)
- `JIRA_API_TOKEN` (not your password)

#### File Parsing Errors
Ensure files are:
- Valid CSV or Excel format
- Contain required columns (email, first name, last name, role)
- Use UTF-8, UTF-16, ISO-8859-1, or Windows-1252 encoding

### Debug Mode
Run with verbose logging to see detailed processing:
```bash
clj -M -m user-upload.core --verbose --dry-run
```

### Logs
Check application logs for detailed error messages:
- JSON formatted for structured logging
- Includes context like ticket ID, tenant, file name

## Development

### Project Structure
```
user-upload-agent/
├── src/user_upload/        # Main source code
│   ├── ai/                 # AI integration (Claude)
│   ├── api/                # Backend API client
│   ├── auth/               # Authentication (tenant, 1Password)
│   ├── jira/               # Jira REST API client
│   ├── parser/             # File parsing (CSV, Excel)
│   ├── workflow/           # Core processing logic
│   ├── config.clj          # Configuration management
│   ├── core.clj            # Main entry point
│   └── log.clj             # Logging configuration
├── exploration/            # Test and exploration scripts
│   ├── ai/                 # AI testing
│   ├── backend-api/        # API testing
│   ├── file-parsing/       # Parser testing
│   ├── jira/               # Jira testing
│   └── workflow/           # Workflow testing
├── resources/              # Configuration and prompts
│   ├── config.edn          # Aero configuration
│   └── prompts/            # AI prompt templates
├── deps.edn                # Dependencies
├── .env.example            # Environment template
└── changelog.md            # Implementation history
```

### Running Tests
```bash
# Run all exploration tests
for test in exploration/**/*test*.clj; do
  echo "Running $test"
  clj -M "$test"
done
```

### Adding New Features
1. Create exploration tests first
2. Implement in appropriate namespace
3. Update changelog.md
4. Test with dry-run mode
5. Deploy with confidence

## Security

- Credentials are never logged or stored in code
- Tenant credentials fetched dynamically from 1Password
- Attachment fingerprinting prevents stale approvals
- Input validation prevents injection attacks
- Error messages sanitized to prevent info leakage

## Support

For issues or questions:
1. Check troubleshooting section above
2. Review exploration tests for examples
3. Check changelog.md for implementation details
4. Contact the development team

## License

[Your License Here]