
---
layout: two-cols
---

# How It Works
## Customer Success Perspective

<v-clicks>

1. **Customer submits ticket** in Jira with user data file
2. **Bot automatically detects** user upload requests
3. **Processes attachment** (CSV/Excel) with AI assistance
4. **Requests approval** if data transformations needed
5. **Creates users** after approval
6. **Reports results** back to Jira ticket

</v-clicks>

::right::

<div class="pt-4">

<div v-if="$slidev.nav.clicks === 1" class="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-lg w-full flex justify-center">

```mermaid {scale: 0.45}
graph TD
    A[Customer Creates<br/>Ticket]
```

</div>

<div v-else-if="$slidev.nav.clicks === 2" class="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-lg w-full flex justify-center">

```mermaid {scale: 0.45}
graph TD
    A[Customer Creates<br/>Ticket] --> B[Bot Detects<br/>Intent]
```

</div>

<div v-else-if="$slidev.nav.clicks === 3" class="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-lg w-full flex justify-center">

```mermaid {scale: 0.45}
graph TD
    A[Customer Creates<br/>Ticket] --> B[Bot Detects<br/>Intent]
    B --> C[Parse Files]
```

</div>

<div v-else-if="$slidev.nav.clicks === 4" class="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-lg w-full flex justify-center">

```mermaid {scale: 0.45}
graph TD
    A[Customer Creates<br/>Ticket] --> B[Bot Detects<br/>Intent]
    B --> C[Parse Files]
    C --> D{Needs<br/>Approval?}
    D -->|Yes| E[Request<br/>Approval]
    D -->|No| F[Create Users]
    
    linkStyle 3 stroke:#ef4444,stroke-width:2px
    linkStyle 4 stroke:#22c55e,stroke-width:2px
```

</div>

<div v-else-if="$slidev.nav.clicks === 5" class="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-lg w-full flex justify-center">

```mermaid {scale: 0.45}
graph TD
    A[Customer Creates<br/>Ticket] --> B[Bot Detects<br/>Intent]
    B --> C[Parse Files]
    C --> D{Needs<br/>Approval?}
    D -->|Yes| E[Request<br/>Approval]
    D -->|No| F[Create Users]
    E --> G[Wait for<br/>Approval]
    
    linkStyle 3 stroke:#ef4444,stroke-width:2px
    linkStyle 4 stroke:#22c55e,stroke-width:2px
```

</div>

<div v-else-if="$slidev.nav.clicks >= 6" class="bg-white dark:bg-gray-800 rounded-lg p-4 shadow-lg w-full flex justify-center">

```mermaid {scale: 0.45}
graph TD
    A[Customer Creates<br/>Ticket] --> B[Bot Detects<br/>Intent]
    B --> C[Parse Files]
    C --> D{Needs<br/>Approval?}
    D -->|Yes| E[Request<br/>Approval]
    D -->|No| F[Create Users]
    E --> G[Wait for<br/>Approval]
    G --> F
    F --> H[Report Results]
    
    linkStyle 3 stroke:#ef4444,stroke-width:2px
    linkStyle 4 stroke:#22c55e,stroke-width:2px
```

</div>

</div>

---
transition: slide-up
---

# Ticket Lifecycle

<div class="grid grid-cols-3 gap-4 mt-8">
  <div v-click class="border-2 border-blue-500 rounded-lg p-4">
    <h3 class="text-blue-500 font-bold mb-2">Open Status</h3>
    <ul class="text-sm space-y-1">
      <li>✓ New tickets detected</li>
      <li>✓ AI analyzes intent</li>
      <li>✓ Files downloaded & parsed</li>
      <li>✓ Data validated</li>
    </ul>
    <div class="mt-3 pt-3 border-t text-xs">
      <strong>Transitions to:</strong>
      <div class="mt-1">→ Review (approval needed)</div>
      <div>→ Info Required (no credentials)</div>
    </div>
  </div>
  
  <div v-click class="border-2 border-yellow-500 rounded-lg p-4">
    <h3 class="text-yellow-500 font-bold mb-2">Review Status</h3>
    <ul class="text-sm space-y-1">
      <li>⏳ Waiting for approval</li>
      <li>📎 Proposed CSV attached</li>
      <li>🔄 Column mappings shown</li>
      <li>💬 Reply "approved" to proceed</li>
    </ul>
    <div class="mt-3 pt-3 border-t text-xs">
      <strong>Transitions to:</strong>
      <div class="mt-1">→ Done (upload successful)</div>
      <div>→ Info Required (failures)</div>
    </div>
  </div>
  
  <div v-click class="border-2 border-red-500 rounded-lg p-4">
    <h3 class="text-red-500 font-bold mb-2">Info Required</h3>
    <ul class="text-sm space-y-1">
      <li>🔐 Missing 1Password entry</li>
      <li>❌ Upload failures occurred</li>
      <li>📝 Setup instructions posted</li>
    </ul>
    <div class="mt-3 pt-3 border-t text-xs">
      <strong>Message examples:</strong>
      <div class="mt-1 italic">"User upload completed with 3 failures"</div>
      <div class="italic">"No 1Password entry found"</div>
    </div>
  </div>
</div>

<div v-click class="mt-6 p-3 bg-gray-100 dark:bg-gray-800 rounded text-sm">
  <strong>Automatic Transitions:</strong>
  <span class="text-xs ml-2">Open→Review (needs approval) • Review→Done (success) • Any→Info Required (errors/missing creds)</span>
</div>

---

# Smart File Processing

<div class="grid grid-cols-2 gap-8">
<div>

## Simple Mode (Direct)
Files with exact column matches:
- `email`
- `first name` 
- `last name`
- `job title`
- `mobile number`
- `teams`
- `user role`

<div class="mt-4 text-green-500">
  ✓ Deterministic system
</div>

</div>
<div v-click>

## Complex Mode (AI-Assisted)
Handles challenging scenarios:
- Multi-sheet Excel files
- Headers not in first row
- Instruction rows before data
- Non-standard column names
- Sheet structure detection

<div class="mt-4 text-blue-500">
  🤖 LLM driven data extraction
</div>

</div>
</div>


---

# Real-World Excel Parsing Challenge

<div class="text-center mb-4">
  <h3 class="text-xl font-semibold">Multi-Sheet Excel with Complex Structure</h3>
  <p class="text-gray-600 dark:text-gray-400">The AI agent navigates through multiple sheets to find and extract user data</p>
</div>

<div class="grid grid-cols-2 gap-4">
  <div>
    <h4 class="text-lg font-bold mb-2 text-orange-500">Sheet 1: Team Configuration</h4>
    <img src="/examplexlsx.png" class="rounded-lg shadow-lg border-2 border-orange-500" />
    <div class="mt-2 text-sm">
      <ul>
        <li>❌ Not user data - just team escalation settings</li>
        <li>🔍 AI recognizes this is configuration, not users</li>
      </ul>
    </div>
  </div>
  
  <div v-click>
    <h4 class="text-lg font-bold mb-2 text-green-500">Sheet 2: User Data</h4>
    <img src="/examplexlsx2.png" class="rounded-lg shadow-lg border-2 border-green-500" />
    <div class="mt-2 text-sm">
      <ul>
        <li>📝 Rows 1-3: Instructions for humans</li>
        <li>📊 Row 5: Actual headers start here</li>
        <li>👥 Row 6+: User data begins</li>
        <li>✅ AI correctly identifies and extracts from row 5 onwards</li>
      </ul>
    </div>
  </div>
</div>

<div v-click class="mt-6 p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
  <div class="text-center font-bold text-blue-600 dark:text-blue-400">
    🎯 AI Capabilities Demonstrated
  </div>
  <div class="grid grid-cols-3 gap-4 mt-3 text-sm">
    <div class="text-center">
      <div class="font-semibold">Sheet Detection</div>
      <div class="text-xs opacity-75">Finds the right sheet with user data</div>
    </div>
    <div class="text-center">
      <div class="font-semibold">Structure Analysis</div>
      <div class="text-xs opacity-75">Skips instruction rows automatically</div>
    </div>
    <div class="text-center">
      <div class="font-semibold">Column Mapping</div>
      <div class="text-xs opacity-75">Maps "USER Role" → "user role"</div>
    </div>
  </div>
</div>

---

# Approval Request Comment

<div class="text-center mb-4">
  <h3 class="text-xl font-semibold">Structured Approval Request</h3>
  <p class="text-gray-600 dark:text-gray-400">The agent posts a detailed comment with all processing information</p>
</div>

<div class="flex justify-center">
  <img src="/approval-comment.png" class="rounded-lg shadow-xl border-2 border-blue-500" style="max-width: 90%;" />
</div>

---

# Approval Request Comment

<div class="text-center mb-4">
  <h3 class="text-xl font-semibold">Structured Approval Request</h3>
  <p class="text-gray-600 dark:text-gray-400">The agent posts a detailed comment with all processing information</p>
</div>

<div class="grid grid-cols-3 gap-4 mt-6">
  <div v-click class="text-center p-3 bg-blue-50 dark:bg-blue-900/20 rounded">
    <div class="text-2xl mb-1">🔍</div>
    <div class="font-semibold text-sm">Full Transparency</div>
    <div class="text-xs opacity-75">See exactly what was processed</div>
  </div>
  <div v-click class="text-center p-3 bg-green-50 dark:bg-green-900/20 rounded">
    <div class="text-2xl mb-1">📎</div>
    <div class="font-semibold text-sm">CSV Attachment</div>
    <div class="text-xs opacity-75">Review the clean data</div>
  </div>
  <div v-click class="text-center p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded">
    <div class="text-2xl mb-1">✅</div>
    <div class="font-semibold text-sm">Simple Approval</div>
    <div class="text-xs opacity-75">Just reply "approved"</div>
  </div>
</div>

---

# Human-in-the-Loop Approval

<div class="text-center mb-4">
  <h3 class="text-xl font-semibold">You Stay in Control</h3>
  <p class="text-gray-600 dark:text-gray-400">After AI processing, the agent creates a clean CSV proposal for your review</p>
</div>

<div class="flex justify-center">
  <img src="/final-upload-proposal.png" class="rounded-lg shadow-xl border-2 border-blue-500" style="max-width: 90%;" />
</div>

--- #11

<div class="grid grid-cols-2 gap-6 mt-6">
  <div>
    <h4 class="font-bold text-green-600 dark:text-green-400 mb-2">✅ What Happened</h4>
    <ul class="text-sm space-y-1">
      <li>📥 Original Excel file processed (Complex multi-sheet)</li>
      <li>🤖 AI mapped columns and extracted data</li>
      <li>📝 Clean CSV created: <code>users-for-approval.csv</code></li>
      <li>🔍 Attached to ticket for review</li>
      <li>⏳ Status changed to "Review"</li>
    </ul>
  </div>
  
  <div v-click>
    <h4 class="font-bold text-blue-600 dark:text-blue-400 mb-2">🎯 Next Steps</h4>
    <ul class="text-sm space-y-1">
      <li>👀 Review the proposed CSV file</li>
      <li>✔️ Verify data looks correct</li>
      <li>💬 Reply with <strong>"approved"</strong> to proceed</li>
      <li>🚀 Agent will then create all users</li>
      <li>📊 Final report posted when complete</li>
    </ul>
  </div>
</div>

<div v-click class="mt-6 p-4 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg">
  <div class="flex items-center justify-center gap-4">
    <div class="text-3xl">🛡️</div>
    <div>
      <div class="font-bold text-yellow-700 dark:text-yellow-400">Safety First</div>
      <div class="text-sm">No users are created until you explicitly approve the proposed data</div>
    </div>
  </div>
</div>

---


# Jira Comment Integration

<div class="text-center mb-4">
  <h3 class="text-xl font-semibold">Automated Status Updates</h3>
  <p class="text-gray-600 dark:text-gray-400">The agent posts detailed comments at every stage</p>
</div>

<div class="grid grid-cols-2 gap-6">
  <div>
    <h4 class="text-lg font-bold mb-2 text-green-500">✅ Successful Upload</h4>
    <img src="/completed.png" class="rounded-lg shadow-lg border border-green-500" />
    <div class="mt-3 text-sm space-y-1">
      <div>• Clear summary of results</div>
      <div>• Created vs existing users</div>
      <div>• Team creation details</div>
      <div>• Automatic ticket transition</div>
    </div>
  </div>
  
  <div v-click>
    <h4 class="text-lg font-bold mb-2 text-orange-500">🔐 Missing Credentials</h4>
    <img src="/missing-password.png" class="rounded-lg shadow-lg border border-orange-500" />
    <div class="mt-3 text-sm space-y-1">
      <div>• Clear setup instructions</div>
      <div>• 1Password entry format</div>
      <div>• Service account pattern</div>
      <div>• Transitions to "Info Required"</div>
    </div>
  </div>
</div>

<div v-click class="mt-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 rounded">
  <div class="text-sm">
    <strong>⚠️ Important 1Password Setup:</strong>
    <div class="mt-1">Credentials must be stored in the <code class="px-1 py-0.5 bg-gray-200 dark:bg-gray-700 rounded">Customer Success (Site Registrations)</code> vault</div>
    <div class="text-xs mt-1 opacity-75">Format: <code>customersolutions+tenant@jesi.io</code></div>
  </div>
</div>

<div v-click class="mt-3 p-3 bg-blue-50 dark:bg-blue-900/20 rounded text-center">
  <strong>Full Audit Trail:</strong> Every action is documented in Jira for compliance and troubleshooting
</div>