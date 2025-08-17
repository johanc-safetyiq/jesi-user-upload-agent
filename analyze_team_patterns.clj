(ns analyze-team-patterns
  (:require [clojure.string :as str]
            [clojure.set :as set]))

(def teams-from-jesi-7693
  ["M&E-Underground Branch-SA"
   "M&E-Underground Branch-SA" 
   "M&E-Underground Branch-SA"
   "M&E-Underground Branch-SA"
   "M&E-Surface Non-IronOre"
   "M&E-Waterwell"
   "M&E-Surface Non-IronOre"
   "M&E-Underground Branch-QLD"
   "M&E-Underground Branch-WAStIves"
   "M&E-Underground Branch-QLD"
   "M&E-Underground Branch-WA Agnew"
   "M&E-Waterwell"
   "M&E-Waterwell"
   "M&E-Underground Branch-WAStIves"
   "M&E-Surface Non-IronOre"
   "M&E-Waterwell"
   "M&E-Waterwell"])

(defn analyze-patterns []
  (println "\n=== Team Pattern Analysis ===\n")
  
  ;; Get unique teams
  (let [unique-teams (distinct teams-from-jesi-7693)]
    (println "Unique teams found:" (count unique-teams))
    (doseq [team unique-teams]
      (println (str "  - \"" team "\"")))
    
    ;; Analyze common prefixes
    (println "\n=== Common Patterns ===")
    
    ;; Split by various delimiters and analyze
    (let [all-tokens (mapcat #(str/split % #"[\s\-]+") unique-teams)
          token-freq (frequencies all-tokens)
          common-tokens (filter #(> (second %) 1) token-freq)]
      
      (println "\nFrequent tokens (appearing more than once):")
      (doseq [[token freq] (sort-by second > common-tokens)]
        (println (str "  \"" token "\" appears " freq " times")))
      
      ;; Check for hierarchical structure
      (println "\n=== Potential Hierarchical Structure ===")
      (println "All teams start with 'M&E-' prefix")
      
      ;; Group by second component
      (let [grouped (group-by #(second (str/split % #"-" 2)) unique-teams)]
        (doseq [[category teams] grouped]
          (println (str "\nCategory: " category))
          (doseq [team teams]
            (println (str "  - " team)))))
      
      ;; Analyze potential ambiguities
      (println "\n=== Ambiguity Analysis ===")
      (doseq [team unique-teams]
        (let [space-split (str/split team #"\s+")
              dash-split (str/split team #"-")
              mixed-split (str/split team #"[\s\-]+")]
          (when (> (count space-split) 1)
            (println (str "\n\"" team "\""))
            (println "  Could be interpreted as:")
            (println (str "    1. Single team: \"" team "\""))
            (when (> (count space-split) 1)
              (println (str "    2. Multiple teams (space-separated): " (pr-str space-split))))
            (when (and (> (count mixed-split) 2) (not= mixed-split space-split))
              (println (str "    3. Multiple teams (mixed separators): " (pr-str mixed-split))))))))))

(defn detect-ambiguous-teams [teams]
  (println "\n=== Ambiguous Team Detection ===\n")
  
  (let [unique-teams (distinct teams)
        ;; Build a token frequency map
        all-tokens (mapcat #(str/split % #"[\s\-]+") teams)
        token-freq (frequencies all-tokens)
        
        ;; Teams that might be ambiguous
        ambiguous (filter (fn [team]
                           ;; Has spaces (not just dashes)
                           (and (str/includes? team " ")
                                ;; Not obviously a single team (e.g., "Non-IronOre")
                                (not (str/includes? team "Non-"))))
                         unique-teams)]
    
    (println "Potentially ambiguous teams:")
    (doseq [team ambiguous]
      (println (str "  - \"" team "\""))
      
      ;; Try different interpretations
      (let [parts (str/split team #"\s+")
            after-me (second (str/split team #"M&E-" 2))]
        
        ;; Check if parts after M&E- could be separate teams
        (when after-me
          (let [potential-teams (str/split after-me #"\s+")]
            (when (> (count potential-teams) 1)
              (println (str "    Alternative: [\"M&E-" (first potential-teams) "\""
                           (apply str " + other teams: " 
                                 (map #(str "\"" % "\" ") (rest potential-teams))) "]"))))))))

(defn propose-ai-disambiguation []
  (println "\n=== AI Disambiguation Proposal ===\n")
  
  (println "Suggested approach for handling ambiguous team names:")
  (println "
1. Pattern Detection Phase:
   - Identify teams with spaces that aren't compound words
   - Look for repeated prefixes/suffixes across the dataset
   - Check against known team patterns in the backend")
  
  (println "
2. AI Analysis Phase:
   - Send ambiguous team names to Claude with context
   - Ask: 'Are these single teams or multiple teams?'
   - Get confidence scores for different interpretations")
  
  (println "
3. User Confirmation Phase:
   - Present ambiguous cases in the approval comment
   - Show AI's interpretation with confidence
   - Allow user to edit the source document with | separators
   - Or provide a way to confirm/correct in the approval")
  
  (println "
4. Example approval comment addition:
   
   **⚠️ Team Name Ambiguity Detected**
   
   The following team assignments may need clarification:
   
   • \"M&E-Underground Branch-WA Agnew\" 
     - Current interpretation: 1 team
     - Possible alternatives: 
       - [\"M&E-Underground\", \"Branch-WA\", \"Agnew\"] (3 teams)
       - [\"M&E-Underground Branch-WA\", \"Agnew\"] (2 teams)
   
   • \"M&E-Surface Non-IronOre\"
     - Current interpretation: 1 team (high confidence - compound word detected)
   
   **To clarify:** Edit the source file using | to separate multiple teams
   Example: \"M&E-Underground|Branch-WA|Agnew\""))

;; Run the analysis
(analyze-patterns)
(detect-ambiguous-teams teams-from-jesi-7693)
(propose-ai-disambiguation)