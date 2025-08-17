(ns user_upload.parser.team_disambiguator
  "Smart team name splitting with automatic detection of pipe or whitespace separation"
  (:require [clojure.string :as str]
            [user_upload.log :as log]))

;; Define regex pattern for pipe-like characters
;; Includes: | (U+007C), ӏ (U+04CF Cyrillic palochka), and other similar chars
(def pipe-pattern #"[|\|ӏ\u04CF\u01C0]")

(defn contains-pipe-like?
  "Check if string contains any pipe-like character"
  [s]
  (boolean (re-find pipe-pattern s)))

(defn detect-team-separator
  "Detect if teams in the dataset use pipe separators (including Unicode variants)"
  [all-teams]
  (let [has-pipes? (some contains-pipe-like? all-teams)]
    (if has-pipes?
      "|"
      " ")))

(defn split-team-name
  "Split a team name based on the detected separator"
  [team-name separator]
  (cond
    ;; Already using pipes - split on any pipe-like character
    (= separator "|")
    (if (contains-pipe-like? team-name)
      (let [parts (->> (str/split team-name pipe-pattern)
                       (map str/trim)
                       (remove str/blank?))]
        {:team team-name
         :has-separator? true
         :separator "|"
         :parts parts
         :suggested (str/join "|" parts)})
      {:team team-name
       :has-separator? false
       :separator "|"
       :parts [team-name]
       :suggested team-name})
    
    ;; Using whitespace - split on spaces
    :else
    (if (str/includes? team-name " ")
      (let [parts (str/split team-name #"\s+")]
        {:team team-name
         :has-separator? true
         :separator " "
         :parts parts
         :suggested (str/join "|" parts)})
      {:team team-name
       :has-separator? false
       :separator " "
       :parts [team-name]
       :suggested team-name})))

(defn analyze-dataset-teams
  "Analyze all teams in a dataset and split based on detected separator"
  [valid-data & [_backend-teams]]
  ;; Note: backend-teams parameter kept for compatibility but ignored
  (let [;; Collect all unique teams from the dataset
        all-teams (->> valid-data
                      (mapcat :teams)
                      distinct
                      set)
        
        ;; Detect which separator is being used
        separator (detect-team-separator all-teams)
        
        ;; Split each team name based on detected separator
        analyses (map #(split-team-name % separator) all-teams)
        
        ;; Filter to just those that were split
        teams-with-separator (filter :has-separator? analyses)]
    
    {:total-teams (count all-teams)
     :teams-with-spaces teams-with-separator  ; Keep old key name for compatibility
     :split-count (count teams-with-separator)
     :all-analyses analyses
     :separator separator
     :using-pipes? (= separator "|")}))

(defn format-splitting-message
  "Format a clear message about the team splitting that was applied"
  [analysis]
  (let [teams-with-spaces (:teams-with-spaces analysis)]
    ;; Only show message if we split on whitespace, not if already using pipes
    (if (or (empty? teams-with-spaces) 
            (:using-pipes? analysis))
      nil  ; No message needed if no teams had spaces or already using pipes
      (str "**📋 Team Names Split on Spaces**\n\n"
           "All team names containing spaces have been automatically split into multiple teams.\n"
           "The attached CSV uses **|** to separate multiple team assignments.\n\n"
           "**Changes Made:**\n"
           (str/join "\n" 
                    (map (fn [team-info]
                          (str "• \"" (:team team-info) "\" → **\"" (:suggested team-info) "\"**"))
                        teams-with-spaces))
           "\n\n"
           "**What This Means:**\n"
           "Each part separated by | becomes a separate team assignment. For example:\n"
           "• \"Team1|Team2|Team3\" assigns the user to all three teams\n\n"
           "**Action Required:**\n"
           "1. Download the attached **users-for-approval.csv**\n"
           "2. Review all team assignments\n"
           "3. If teams should NOT be split:\n"
           "   - Remove the | separator to keep as one team\n"
           "   - Example: Change \"M&E-Surface|Non-IronOre\" back to \"M&E-Surface Non-IronOre\"\n"
           "4. Reply **'approved'** when ready"))))

(defn apply-team-splitting
  "Apply team splitting to the user data"
  [valid-data analysis]
  (if (:using-pipes? analysis)
    ;; If already using pipes, split on any pipe-like character
    (map (fn [user]
          (update user :teams
                 (fn [teams]
                   (mapcat (fn [team]
                            (if (contains-pipe-like? team)
                              ;; Split on any pipe-like character and trim each part
                              (->> (str/split team pipe-pattern)
                                   (map str/trim)
                                   (remove str/blank?))
                              [team]))
                          teams))))
        valid-data)
    ;; Original logic for whitespace splitting
    (let [;; Create a map of original team name to suggested split
          splitting-map (into {} (map (fn [team-info]
                                        [(:team team-info) (:suggested team-info)])
                                      (:teams-with-spaces analysis)))]
      (map (fn [user]
            (update user :teams
                   (fn [teams]
                     ;; For each team, either use the split version or keep as-is
                     (mapcat (fn [team]
                              (if-let [split-version (get splitting-map team)]
                                ;; If it was split, expand to multiple teams
                                (if (str/includes? split-version "|")
                                  (str/split split-version #"\|")
                                  [split-version])
                                ;; Otherwise keep as single team
                                [team]))
                            teams))))
          valid-data))))

(defn enhance-approval-comment
  "Add team splitting message to the approval comment"
  [original-comment analysis]
  (let [message (format-splitting-message analysis)]
    (if message
      (str original-comment "\n\n" message)
      original-comment)))