# Jira Cloud REST API Guide

## Overview

This guide covers common operations with the Jira Cloud REST API v3, including authentication, fetching issues, managing comments, and uploading attachments.

## Prerequisites

- Atlassian account email
- API token (generate at https://id.atlassian.com/manage-profile/security/api-tokens)
- Your Jira Cloud domain (e.g., `your-domain.atlassian.net`)

## Authentication

All requests use Basic Authentication with your email and API token:

```bash
-u 'your-email@example.com:YOUR_API_TOKEN'
```

## Base URL

```
https://your-domain.atlassian.net/rest/api/3/
```

## Common Operations

### 1. Get Issue Details

Fetch complete issue information including all fields:

```bash
curl -u 'email:token' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY' | jq
```

With specific fields only:

```bash
curl -u 'email:token' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY?fields=summary,status,description' | jq
```

### 2. Get All Comments

Fetch all comments for a specific issue:

```bash
curl -u 'email:token' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/comment' | jq
```

**Response Structure:**
```json
{
  "startAt": 0,
  "maxResults": 100,
  "total": 2,
  "comments": [
    {
      "id": "44353",
      "author": {...},
      "body": {
        "type": "doc",
        "content": [...]
      },
      "created": "2025-08-12T05:33:50.004-0500",
      "updated": "2025-08-12T05:33:50.004-0500"
    }
  ]
}
```

### 3. Add a Comment

Post a new comment to an issue:

```bash
curl -X POST \
  -u 'email:token' \
  -H "Content-Type: application/json" \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/comment' \
  -d '{
    "body": {
      "type": "doc",
      "version": 1,
      "content": [
        {
          "type": "paragraph",
          "content": [
            {
              "type": "text",
              "text": "Your comment text here"
            }
          ]
        }
      ]
    }
  }'
```

### 4. Upload Attachments

Upload files to an existing issue:

```bash
curl -X POST \
  -u 'email:token' \
  -H 'X-Atlassian-Token: no-check' \
  --form 'file=@"/path/to/file.pdf"' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/attachments'
```

**Important Notes:**
- Header `X-Atlassian-Token: no-check` is **required**
- Use `multipart/form-data` format
- Field name must be `file`
- Multiple files can be uploaded in one request:

```bash
curl -X POST \
  -u 'email:token' \
  -H 'X-Atlassian-Token: no-check' \
  --form 'file=@"file1.pdf"' \
  --form 'file=@"file2.png"' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/attachments'
```

### 5. Search Issues with JQL

Search for issues using Jira Query Language:

```bash
curl -u 'email:token' \
  -G --data-urlencode 'jql=project=JESI AND status="In Progress"' \
  'https://your-domain.atlassian.net/rest/api/3/search' | jq
```

### 6. Create an Issue

Create a new issue:

```bash
curl -X POST \
  -u 'email:token' \
  -H "Content-Type: application/json" \
  'https://your-domain.atlassian.net/rest/api/3/issue' \
  -d '{
    "fields": {
      "project": {"key": "JESI"},
      "summary": "Issue title",
      "description": {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "Issue description"
              }
            ]
          }
        ]
      },
      "issuetype": {"name": "Task"}
    }
  }'
```

### 7. Update an Issue

Update issue fields:

```bash
curl -X PUT \
  -u 'email:token' \
  -H "Content-Type: application/json" \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY' \
  -d '{
    "fields": {
      "summary": "Updated title",
      "description": {
        "type": "doc",
        "version": 1,
        "content": [
          {
            "type": "paragraph",
            "content": [
              {
                "type": "text",
                "text": "Updated description"
              }
            ]
          }
        ]
      }
    }
  }'
```

### 8. Transition Issue Status

Move an issue through workflow:

```bash
# First, get available transitions
curl -u 'email:token' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/transitions' | jq

# Then transition the issue
curl -X POST \
  -u 'email:token' \
  -H "Content-Type: application/json" \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/transitions' \
  -d '{
    "transition": {
      "id": "21"
    }
  }'
```

### 9. Get Attachment Metadata

Check attachment settings and limits:

```bash
curl -u 'email:token' \
  'https://your-domain.atlassian.net/rest/api/3/attachment/meta' | jq
```

### 10. Delete a Comment

Delete a specific comment:

```bash
curl -X DELETE \
  -u 'email:token' \
  'https://your-domain.atlassian.net/rest/api/3/issue/ISSUE-KEY/comment/COMMENT-ID'
```

## Python Examples

### Setup

```python
import requests
from requests.auth import HTTPBasicAuth
import json

# Configuration
EMAIL = "your-email@example.com"
API_TOKEN = "your-api-token"
DOMAIN = "your-domain.atlassian.net"
BASE_URL = f"https://{DOMAIN}/rest/api/3"

auth = HTTPBasicAuth(EMAIL, API_TOKEN)
headers = {"Accept": "application/json", "Content-Type": "application/json"}
```

### Get Comments

```python
def get_comments(issue_key):
    url = f"{BASE_URL}/issue/{issue_key}/comment"
    response = requests.get(url, auth=auth, headers=headers)
    if response.status_code == 200:
        comments = response.json()
        for comment in comments['comments']:
            print(f"Comment ID: {comment['id']}")
            print(f"Author: {comment['author']['displayName']}")
            print(f"Created: {comment['created']}")
            # Extract text from ADF format
            body = comment['body']['content'][0]['content'][0]['text']
            print(f"Text: {body}\n")
    return response.json()
```

### Add Comment

```python
def add_comment(issue_key, comment_text):
    url = f"{BASE_URL}/issue/{issue_key}/comment"
    payload = {
        "body": {
            "type": "doc",
            "version": 1,
            "content": [
                {
                    "type": "paragraph",
                    "content": [
                        {
                            "type": "text",
                            "text": comment_text
                        }
                    ]
                }
            ]
        }
    }
    response = requests.post(url, json=payload, auth=auth, headers=headers)
    return response.json()
```

### Upload Attachment

```python
def upload_attachment(issue_key, file_path):
    url = f"{BASE_URL}/issue/{issue_key}/attachments"
    headers = {"X-Atlassian-Token": "no-check"}
    
    with open(file_path, 'rb') as f:
        files = {'file': f}
        response = requests.post(url, auth=auth, headers=headers, files=files)
    
    return response.json()
```

## Node.js Examples

### Setup

```javascript
const fetch = require('node-fetch');
const FormData = require('form-data');
const fs = require('fs');

const EMAIL = 'your-email@example.com';
const API_TOKEN = 'your-api-token';
const DOMAIN = 'your-domain.atlassian.net';
const BASE_URL = `https://${DOMAIN}/rest/api/3`;

const auth = Buffer.from(`${EMAIL}:${API_TOKEN}`).toString('base64');
const headers = {
    'Authorization': `Basic ${auth}`,
    'Accept': 'application/json',
    'Content-Type': 'application/json'
};
```

### Get Comments

```javascript
async function getComments(issueKey) {
    const url = `${BASE_URL}/issue/${issueKey}/comment`;
    const response = await fetch(url, { headers });
    const data = await response.json();
    
    data.comments.forEach(comment => {
        console.log(`Comment ID: ${comment.id}`);
        console.log(`Author: ${comment.author.displayName}`);
        console.log(`Created: ${comment.created}`);
        const text = comment.body.content[0].content[0].text;
        console.log(`Text: ${text}\n`);
    });
    
    return data;
}
```

### Upload Attachment

```javascript
async function uploadAttachment(issueKey, filePath) {
    const url = `${BASE_URL}/issue/${issueKey}/attachments`;
    const form = new FormData();
    form.append('file', fs.createReadStream(filePath));
    
    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Authorization': `Basic ${auth}`,
            'X-Atlassian-Token': 'no-check'
        },
        body: form
    });
    
    return await response.json();
}
```

## Error Handling

Common HTTP status codes and their meanings:

| Status | Meaning | Common Causes |
|--------|---------|---------------|
| 200 | Success | Request completed successfully |
| 201 | Created | Resource created successfully |
| 204 | No Content | Request succeeded, no content to return |
| 400 | Bad Request | Invalid request format or parameters |
| 401 | Unauthorized | Invalid credentials or expired token |
| 403 | Forbidden | No permission for this operation |
| 404 | Not Found | Issue or resource doesn't exist |
| 415 | Unsupported Media Type | Missing or wrong Content-Type header |

### Common Error Solutions

1. **415 with attachments**: Ensure you're using `multipart/form-data`
2. **403 "XSRF check failed"**: Add header `X-Atlassian-Token: no-check`
3. **401 Unauthorized**: Check API token validity and format
4. **404 Not Found**: Verify issue key and project permissions

## Rate Limits

Jira Cloud implements rate limiting. If you exceed limits:
- You'll receive a 429 status code
- Check `Retry-After` header for wait time
- Implement exponential backoff in production code

## Best Practices

1. **Use field IDs** instead of names when possible for reliability
2. **Paginate results** for large datasets using `startAt` and `maxResults`
3. **Cache responses** when appropriate to reduce API calls
4. **Use JQL efficiently** - be specific to reduce response size
5. **Handle errors gracefully** with proper error messages
6. **Store credentials securely** - use environment variables, not hardcoded values
7. **Use batch operations** when available (e.g., bulk issue updates)

## Useful Resources

- [Official API Documentation](https://developer.atlassian.com/cloud/jira/platform/rest/v3/)
- [JQL Documentation](https://support.atlassian.com/jira/docs/jql/)
- [Atlassian Document Format (ADF)](https://developer.atlassian.com/cloud/jira/platform/apis/document/structure/)
- [API Token Management](https://id.atlassian.com/manage-profile/security/api-tokens)
- [Postman Collection](https://developer.atlassian.com/cloud/jira/platform/rest/v3/intro/#postman)

## Environment Variables Setup

Create a `.env` file (never commit to version control):

```bash
ATLASSIAN_EMAIL=your-email@example.com
ATLASSIAN_API_KEY=your-api-token-here
JIRA_DOMAIN=your-domain.atlassian.net
```

Use in bash scripts:

```bash
#!/bin/bash
source .env

curl -u "${ATLASSIAN_EMAIL}:${ATLASSIAN_API_KEY}" \
  "https://${JIRA_DOMAIN}/rest/api/3/issue/ISSUE-KEY"
```