;; Example of how to integrate team disambiguation into the approval workflow
;; This would be added to src/user_upload/workflow/approval.clj

(ns integrate-team-disambiguation
  (:require [user-upload.parser.team-disambiguator :as disambig]))

(defn enhanced-request-approval
  "Enhanced version of request-approval that includes team disambiguation"
  [ticket-key valid-data invalid-data tenant]
  
  ;; 1. Analyze teams for ambiguity
  (let [ambiguity-analysis (disambig/analyze-dataset-teams valid-data)
        ambiguous-count (:ambiguous-count ambiguity-analysis)]
    
    (when (> ambiguous-count 0)
      (log/warn "Detected ambiguous team names" 
               {:count ambiguous-count
                :teams (map :team (:ambiguous-teams ambiguity-analysis))}))
    
    ;; 2. Generate the standard approval request
    (let [standard-comment (generate-approval-request valid-data invalid-data tenant)
          
          ;; 3. Add ambiguity warnings if needed
          enhanced-comment (if (> ambiguous-count 0)
                            (disambig/enhance-approval-comment standard-comment ambiguity-analysis)
                            standard-comment)]
      
      ;; 4. Optionally get AI suggestions for ambiguous teams
      (when (and (> ambiguous-count 0) 
                (< ambiguous-count 10))  ; Only use AI for small batches
        (try
          (let [ai-suggestions (disambig/get-ai-disambiguation 
                              (:ambiguous-teams ambiguity-analysis)
                              {:organization "Mining & Engineering company"})]
            (log/info "Got AI disambiguation suggestions" {:suggestions ai-suggestions}))
          (catch Exception e
            (log/warn "Failed to get AI suggestions" {:error (.getMessage e)}))))
      
      ;; 5. Post the enhanced comment
      (jira/add-comment ticket-key enhanced-comment)
      
      ;; 6. Return result with ambiguity info
      {:success true
       :comment-posted true
       :ambiguous-teams ambiguous-count
       :message (if (> ambiguous-count 0)
                 (str "Approval requested with " ambiguous-count " ambiguous team names flagged")
                 "Approval requested successfully")})))

;; Example of the enhanced approval comment that would be generated:
(def example-enhanced-comment
"# User Upload Approval Request

**Tenant:** Qbirt
**Valid Users:** 17
**Invalid/Skipped:** 0

## Summary of Valid Users

| Email | Name | Teams | Role |
|-------|------|-------|------|
| scottandrews257@gmail.com | Scott Andrews | M&E-Underground Branch-SA | TEAM MEMBER |
| bradleybrown288@gmail.com | Bradley Brown | M&E-Underground Branch-SA | TEAM MEMBER |
| ... | ... | ... | ... |

**To approve:** Reply with 'approved' | **CSV attached:** users-for-approval.csv

**⚠️ Team Name Clarification Needed**

The following team assignments contain spaces and may represent multiple teams:

• \"M&E-Underground Branch-WA Agnew\"
  - Current interpretation: 1 team
  - Could be: [\"M&E-Underground\" \"Branch-WA\" \"Agnew\"] (3 teams)
  - AI suggestion: single team (confidence: medium)
    Reason: Likely a hierarchical team structure with location

• \"M&E-Underground Branch-SA\"
  - Current interpretation: 1 team  
  - Could be: [\"M&E-Underground\" \"Branch-SA\"] (2 teams)
  - AI suggestion: single team (confidence: high)
    Reason: Standard branch naming pattern

• \"M&E-Surface Non-IronOre\"
  - Current interpretation: 1 team
  - AI suggestion: single team (confidence: high)
    Reason: 'Non-IronOre' is a compound descriptor

**To clarify:** Edit the source file using | to separate multiple teams
Example: \"M&E-Underground|Branch-WA|Agnew\"

Or reply 'approved' if the current interpretation is correct.")

(println "Integration example showing how team disambiguation would enhance the approval workflow:")
(println example-enhanced-comment)