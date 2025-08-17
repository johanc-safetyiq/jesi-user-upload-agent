# Team Disambiguation Issue - Implementation Status and Remaining Work

## Executive Summary

The user upload system needs to handle ambiguous team names that contain spaces (e.g., "M&E-Underground Branch-WA Agnew") which could represent either:
1. A single team with spaces in its name
2. Multiple teams that should be separated

We've implemented a team disambiguation system, but it's not currently working in production due to backend team fetching issues during the approval workflow.

## Current Issue

### Problem Statement
When processing JESI-7693 on 2025-08-15, the system did NOT apply team disambiguation despite having team names with spaces that could be multiple teams:
- Input: `"M&E-Underground Branch-WA Agnew"`
- Expected: `"M&E-Underground|Branch-WA|Agnew"` (if these exist as separate teams)
- Actual: `"M&E-Underground Branch-WA Agnew"` (kept as single team)

### Evidence
1. The generated CSV file (`users-for-approval.csv`) contains NO pipe separators (`|`)
2. The approval comment doesn't mention team disambiguation
3. Teams with spaces were kept as single teams without analysis

## System Architecture

### Components Implemented

#### 1. Team Disambiguator Module
**Location**: `src/user_upload/parser/team_disambiguator.clj`

**Key Functions**:
- `analyze-team-ambiguity`: Determines if a team name might be multiple teams
- `analyze-dataset-teams`: Analyzes all teams in a dataset
- `apply-team-disambiguation`: Applies best guess to split teams
- `format-ambiguity-warning`: Formats warning for approval comment

**Algorithm**:
1. Check if team name has spaces
2. Check for compound words (Non-, Sub-, etc.) → keep as single team
3. Check if full name exists in backend → keep as single team
4. Check if parts exist separately in backend → split with `|`
5. If uncertain → keep as single team with low confidence

#### 2. Integration Points
**Modified Files**:
- `src/user_upload/workflow/approval.clj` - Integrated disambiguation into approval workflow
- `src/user_upload/workflow/orchestrator.clj` - Added backend team fetching

### What Works

When tested in isolation (`test_disambiguation_direct.clj`), the system correctly:
- Detects ambiguous team names
- Splits teams when parts exist in backend
- Keeps compound words together
- Generates CSV with `|` separators
- Formats warning messages

**Test Output Example**:
```
Input: "M&E-Underground Branch-WA Agnew"
Output: "M&E-Underground|Branch-WA|Agnew" (when these exist as separate teams)
```

### What Doesn't Work

1. **Backend Team Fetching**: During the approval request phase, backend teams aren't being successfully fetched
2. **Authentication Timing**: Authentication happens too late in the workflow
3. **Fallback Logic**: No disambiguation happens when backend teams aren't available

## Root Cause Analysis

### The Flow Problem

**Current Flow**:
1. Parse Excel file
2. Validate data
3. Check if approval needed → **PROBLEM: No auth here**
4. Request approval (tries to fetch backend teams but not authenticated)
5. Only authenticate when proceeding with upload (after approval)

**Desired Flow**:
1. Parse Excel file
2. Validate data
3. **Authenticate if credentials available**
4. **Fetch backend teams**
5. Check if approval needed
6. Request approval with disambiguation

### Code Issues

#### Issue 1: Authentication Timing
**Location**: `src/user_upload/workflow/orchestrator.clj:745-758`

The code attempts to fetch backend teams but authentication might not be established:
```clojure
(let [backend-teams (when credentials-found
                     (try
                       (let [email-template "customersolutions+%s@jesi.io"
                             cred-result (op/get-tenant-credentials tenant email-template)]
                         (when (:success cred-result)
                           (api/login (:email cred-result) (:password cred-result))
                           (let [teams-response (api/search-teams)]
                             (map :name (:teams teams-response)))))
                       (catch Exception e
                         (log/debug "Could not fetch backend teams for disambiguation" 
                                   {:error (.getMessage e)})
                         nil)))]
```

**Problem**: This code runs but fails silently, resulting in `nil` backend teams.

#### Issue 2: No Fallback Logic
**Location**: `src/user_upload/parser/team_disambiguator.clj`

When backend teams are empty/nil, the algorithm defaults to keeping teams as-is rather than applying heuristics.

## Proposed Solution

### Phase 1: Fix Backend Team Fetching

1. **Move Authentication Earlier**
   - Authenticate immediately after detecting it's a user upload ticket
   - Cache the authentication token for the entire processing flow
   - Ensure backend teams are fetched successfully before approval

2. **Add Robust Error Handling**
   - Log when backend team fetching fails
   - Add metrics/monitoring for this step
   - Don't silently fail

### Phase 2: Implement Fallback Heuristics

When backend teams aren't available, apply these rules:

1. **Pattern Detection**:
   - If contains "Branch-XX" where XX is a state code → likely separate teams
   - If contains location names (Agnew, StIves) → likely separate teams
   - If contains "Non-" → keep together (compound word)

2. **Frequency Analysis**:
   - Analyze all teams in the dataset
   - If parts appear independently elsewhere → likely separate teams
   - Example: If "M&E-Underground" appears alone AND "Branch-SA" appears with other prefixes

3. **Conservative Approach**:
   - When uncertain, keep as single team but flag for review
   - Always communicate uncertainty in approval comment

### Phase 3: Enhanced User Communication

Modify the approval comment to include:

```markdown
**⚠️ Team Name Disambiguation Applied**

The following team assignments contained spaces and have been interpreted as shown below.
**The attached CSV reflects our best guess using | to separate teams where appropriate.**

• "M&E-Underground Branch-WA Agnew"
  - **Applied interpretation:** "M&E-Underground|Branch-WA|Agnew"
  - Reason: Parts exist as separate teams in backend
  - Confidence: Medium
  
• "M&E-Surface Non-IronOre"
  - **Applied interpretation:** "M&E-Surface Non-IronOre" (kept as-is)
  - Reason: Contains compound word
  - Confidence: High

**Action Required:**
• Review the CSV attachment to verify team assignments are correct
• Edit the CSV if any teams need adjustment
• Reply 'approved' when the CSV is correct
```

## Implementation Checklist

### Immediate Fixes (Priority 1)
- [ ] Fix authentication timing in orchestrator.clj
- [ ] Add logging for backend team fetch success/failure
- [ ] Test with JESI-7693 to verify teams are fetched

### Disambiguation Logic (Priority 2)
- [ ] Implement fallback heuristics when backend unavailable
- [ ] Add pattern detection for common team structures
- [ ] Implement frequency analysis across dataset

### User Experience (Priority 3)
- [ ] Ensure warning always appears when ambiguity detected
- [ ] Make warning message clearer about what was changed
- [ ] Add examples in the warning message

### Testing (Priority 4)
- [ ] Create comprehensive test suite for disambiguation
- [ ] Test with various team name patterns
- [ ] Test with and without backend teams available

## Testing Instructions

### Manual Test Case
1. Reset JESI-7693 to Open status
2. Run: `clojure -M -m user-upload.core process-ticket JESI-7693`
3. Check the approval comment for disambiguation warning
4. Download the CSV and verify `|` separators where appropriate
5. Verify teams like "M&E-Underground Branch-WA Agnew" are split

### Verification Script
```bash
# Check if disambiguation was applied
cd /Users/johan/Work/brisbane/jesi-system/user-upload-agent
clojure -M fetch_jesi_7693.clj

# Look for:
# 1. Disambiguation warning in comments
# 2. Pipe separators in CSV teams column
```

## Expected Outcomes

When complete, the system should:

1. **Always detect** team names with spaces as potentially ambiguous
2. **Apply best guess** using backend data when available
3. **Use heuristics** when backend data unavailable
4. **Generate CSV** with `|` separators for multiple teams
5. **Add warning** to approval comment explaining changes
6. **Allow CS review** and manual correction if needed

## Code Locations Reference

- **Main disambiguation logic**: `src/user_upload/parser/team_disambiguator.clj`
- **Approval workflow integration**: `src/user_upload/workflow/approval.clj:368-411`
- **Backend team fetching**: `src/user_upload/workflow/orchestrator.clj:745-762`
- **Test script**: `test_disambiguation_direct.clj`
- **Debug tool**: `fetch_jesi_7693.clj`

## Related Files
- Original ticket: JESI-7693
- Sample data: Teams with spaces that need disambiguation
- Test data location: `/Users/johan/Work/brisbane/jesi-system/user-upload-agent/downloads/users-for-approval.csv`

## Contact & Context
- Date reported: 2025-08-15
- Current behavior: Teams kept as single values
- Desired behavior: Smart splitting with user verification
- Business impact: Incorrect team assignments may prevent users from accessing correct resources

## Next Steps

1. **Verify the issue**: Run `fetch_jesi_7693.clj` to confirm current state
2. **Fix authentication**: Ensure backend teams are fetched
3. **Test locally**: Use `test_disambiguation_direct.clj` to verify logic
4. **Deploy and test**: Process JESI-7693 again
5. **Monitor**: Check logs for successful disambiguation

---

*This document provides complete context for implementing team name disambiguation in the user upload system. The code is mostly complete but needs fixes to work in production.*