# 1Password CLI Integration Guide

## Overview

The User Upload Agent uses 1Password CLI with a service account token to securely fetch tenant-specific credentials. This eliminates the need to store passwords in code or environment files.

## Prerequisites

1. **1Password CLI** (`op`) installed
2. **Service Account Token** configured in `.env` file
3. **Access to Vault**: "Customer Support (Site Registrations)"

## Setup

### 1. Install 1Password CLI

```bash
# macOS
brew install --cask 1password-cli

# Verify installation
op --version
```

### 2. Configure Service Account Token

Add to your `.env` file:
```bash
export OP_SERVICE_ACCOUNT_TOKEN=ops_eyJzaWduSW5BZGRyZXNzIjo...
```

The token provides automated access without interactive signin.

### 3. Verify Configuration

```bash
# Source environment
source .env

# Test access
op vault list

# Should show "Customer Support (Site Registrations)" vault
```

## Usage

### Command Line Examples

#### Search and Get Credentials by Tenant

```bash
# Search by tenant name (e.g., "elecnor")
source .env && op item list --vault "Customer Support (Site Registrations)" --format=json | \
  jq -r '.[] | select(.title | test("elecnor"; "i")) | .id' | \
  xargs -I {} op item get {} --vault "Customer Support (Site Registrations)" \
  --fields label=username,label=password --reveal
```

#### Direct Retrieval by Title

```bash
# If you know the exact title
source .env && op item get "Elecnor" \
  --vault "Customer Support (Site Registrations)" \
  --fields label=username,label=password --reveal
```

Output format: `username,password`

### Programmatic Usage (Clojure)

```clojure
(require '[user-upload.auth.onepassword :as op])

;; Check availability
(op/check-op-availability)
;; => {:available true, :authenticated true, :version "Service account authenticated"}

;; Fetch credentials for a tenant
(op/fetch-credentials-for-tenant "elecnor")
;; => {:success true, 
;;     :email "customersolutions+elecnor@jesi.io", 
;;     :password "secretpassword",
;;     :cached false}

;; Get credentials with email template
(op/get-tenant-credentials "elecnor")
;; => {:success true,
;;     :email "customersolutions+elecnor@jesi.io",
;;     :password "secretpassword",
;;     :cached false}
```

## How It Works

### 1. Tenant Extraction
The system extracts the tenant name from Jira tickets by looking for email patterns:
- Pattern: `customersolutions+<tenant>@jesi.io`
- Example: From "customersolutions+elecnor@jesi.io" → tenant = "elecnor"

### 2. Credential Retrieval Flow

```
1. Check cache for tenant credentials
   ↓ (if not cached)
2. Try direct retrieval: op item get "<tenant>"
   ↓ (if not found)
3. Search vault: op item list --vault "..."
   ↓ (find matching item)
4. Get credentials: op item get <id> --fields ...
   ↓
5. Cache credentials in memory
   ↓
6. Return credentials
```

### 3. Caching
- Credentials are cached in memory during runtime
- Cache prevents repeated 1Password CLI calls
- Cache can be cleared with `(op/clear-credential-cache)`

## Vault Structure

The "Customer Support (Site Registrations)" vault should contain:
- **Item Title**: Tenant name (e.g., "Elecnor", "Acme Corp")
- **Username Field**: Service account email (customersolutions+tenant@jesi.io)
- **Password Field**: Service account password

## Security Considerations

1. **Service Account Token**
   - Store securely in `.env` file
   - Never commit to version control
   - Rotate periodically

2. **Credential Caching**
   - Credentials cached only in memory
   - Cache cleared on application restart
   - No persistent storage

3. **Access Control**
   - Service account has read-only access
   - Limited to specific vault
   - Audit trail in 1Password

## Troubleshooting

### Token Not Working
```bash
# Check if token is set
echo $OP_SERVICE_ACCOUNT_TOKEN

# Verify token works
op vault list
```

### Tenant Not Found
```bash
# List all items in vault
op item list --vault "Customer Support (Site Registrations)"

# Search for partial match
op item list --vault "Customer Support (Site Registrations)" | grep -i tenant
```

### Debugging in Application
```clojure
;; Enable debug logging
(require '[clojure.tools.logging :as log])
(log/set-level! :debug)

;; Check cache stats
(op/get-cache-stats)

;; Clear cache and retry
(op/clear-credential-cache)
(op/fetch-credentials-for-tenant "tenant-name")
```

## Testing

### Manual Test
```bash
# Test with known tenant
source .env
clj -M -e "(require '[user-upload.auth.onepassword :as op]) \
           (prn (op/fetch-credentials-for-tenant \"elecnor\"))"
```

### Integration Test
```bash
# Run full workflow test
clj -M exploration/workflow/onepassword-test.clj
```

## Best Practices

1. **Tenant Naming**: Use consistent, lowercase tenant names
2. **Error Handling**: Always check `:success` flag in response
3. **Fallback Strategy**: Have manual override for missing entries
4. **Monitoring**: Log credential fetch attempts and failures
5. **Cache Management**: Clear cache on security events

## Migration from Old System

If migrating from hardcoded credentials:
1. Create 1Password entries for each tenant
2. Use tenant name as item title
3. Set username to customersolutions+tenant@jesi.io
4. Store actual password in password field
5. Test retrieval before removing old credentials