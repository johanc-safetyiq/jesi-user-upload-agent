(ns analyze-team-patterns2
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(def teams-from-jesi-7693
  ["M&E-Underground Branch-SA"
   "M&E-Surface Non-IronOre"
   "M&E-Waterwell"
   "M&E-Underground Branch-QLD"
   "M&E-Underground Branch-WAStIves"
   "M&E-Underground Branch-WA Agnew"])

(defn analyze-team-patterns []
  (println "\n=== Team Pattern Analysis ===\n")
  (println "Unique teams from JESI-7693:")
  (doseq [team (distinct teams-from-jesi-7693)]
    (println (str "  - \"" team "\"")))
  
  (println "\n=== Ambiguity Analysis ===")
  (println "\nTeams with spaces that might be multiple teams:")
  
  (doseq [team (distinct teams-from-jesi-7693)]
    (when (str/includes? team " ")
      (println (str "\n\"" team "\""))
      (println "  Possible interpretations:")
      (println (str "    1. Single team: \"" team "\" (current)"))
      
      ;; Check for "Non-" compound words
      (if (str/includes? team "Non-")
        (println "    (High confidence - compound word detected)")
        (do
          ;; Split by spaces
          (let [space-parts (str/split team #"\s+")]
            (when (> (count space-parts) 1)
              (println (str "    2. Multiple teams: " (pr-str space-parts)))))
          
          ;; Special handling for M&E prefix
          (when (str/starts-with? team "M&E-")
            (let [after-prefix (subs team 4)
                  parts (str/split after-prefix #"\s+")]
              (when (> (count parts) 1)
                (println (str "    3. M&E + separate teams: [\"M&E-" (first parts) "\""
                             (when (> (count parts) 1)
                               (str ", " (pr-str (rest parts)))) "]"))))))))))

(defn propose-solution []
  (println "\n=== Proposed Solution ===\n")
  (println "
1. IMMEDIATE FIX - Add disambiguation to approval comment:
   - Detect teams with spaces
   - Add warning section to approval comment
   - Request user to clarify with | separators

2. PATTERN DETECTION (deterministic):
   - Analyze all team names in the dataset
   - Find common prefixes/suffixes
   - Check against backend for existing teams
   - Flag teams that don't exist as potentially mis-parsed

3. AI-POWERED DISAMBIGUATION:
   - Send ambiguous teams to Claude
   - Provide context about the organization
   - Get confidence scores for interpretations
   
4. INTERACTIVE RESOLUTION:
   - Show ambiguous cases in approval comment
   - Allow inline editing before approval
   - Or request source file update with | separators

Example enhanced approval comment:
----------------------------------------
**⚠️ Team Name Clarification Needed**

The following team assignments contain spaces and may represent multiple teams:

• \"M&E-Underground Branch-WA Agnew\" 
  - Interpreted as: 1 team
  - Could be: [\"M&E-Underground\", \"Branch-WA\", \"Agnew\"] (3 teams)
  - Or: [\"M&E-Underground Branch-WA\", \"Agnew\"] (2 teams)
  
• \"M&E-Surface Non-IronOre\"
  - Interpreted as: 1 team (likely correct - compound word)

**To fix:** Edit the source file using | to separate multiple teams
Example: \"M&E-Underground|Branch-WA|Agnew\"

Or reply 'approved' if the current interpretation is correct.
----------------------------------------"))

;; Run analysis
(analyze-team-patterns)
(propose-solution)