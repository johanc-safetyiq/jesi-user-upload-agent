(ns user_upload.ai.mapping
  "AI-powered column mapping for user upload files.
   
   This module maps file column headers to expected schema fields using Claude AI."
  (:require [user_upload.ai.claude :as claude]
            [clojure.tools.logging :as log]
            [clojure.string :as str]))

(def ^:private standard-schema
  "Standard expected fields for user uploads."
  ["email" "first name" "last name" "job title" "mobile number" "teams" "user role"])

(defn ai-column-mapping
  "Use Claude AI to map column headers to expected fields.
   
   Args:
     expected-fields - Vector of expected field names
     file-headers - Vector of actual file column headers
   
   Returns:
     Map with :method :ai and either:
       :success true, :mapping, :unmapped
       :success false, :error string"
  [expected-fields file-headers]
  (log/debug (format "AI column mapping %d headers to %d expected fields"
                     (count file-headers) (count expected-fields)))
  
  (let [result (claude/invoke-column-mapping expected-fields file-headers)]
    (if (:success result)
      {:method :ai
       :success true
       :mapping (:mapping result)
       :unmapped (:unmapped result)}
      {:method :ai
       :success false
       :error (:error result)})))

(defn map-columns
  "Map file column headers to expected schema fields using AI.
   
   Args:
     expected-fields - Vector of expected field names (default: standard schema)
     file-headers - Vector of actual file column headers
   
   Returns:
     Map with keys:
       :success - Boolean indicating if mapping succeeded
       :mapping - Map of file-header -> expected-field
       :unmapped - Vector of expected fields not found
       :error - Error message (if failed)"
  [expected-fields file-headers]
  
  (let [expected (or expected-fields standard-schema)]
    (log/info (format "AI mapping %d file headers to %d expected fields"
                      (count file-headers) (count expected)))
    
    (let [ai-result (ai-column-mapping expected file-headers)]
      (if (:success ai-result)
        ;; AI succeeded
        (do
          (log/info (format "AI mapped %d/%d fields"
                            (count (:mapping ai-result))
                            (count expected)))
          {:success true
           :mapping (:mapping ai-result)
           :unmapped (:unmapped ai-result)})
        
        ;; AI failed
        (do
          (log/error "AI column mapping failed" {:error (:error ai-result)})
          {:success false
           :error (:error ai-result)})))))

(defn validate-mapping
  "Validate a column mapping result.
   
   Args:
     mapping-result - Result from map-columns
     required-fields - Vector of fields that must be mapped
   
   Returns:
     Map with :valid boolean and :issues vector"
  [mapping-result required-fields]
  (let [mapping (:mapping mapping-result)
        unmapped (:unmapped mapping-result)
        mapped-fields (set (vals mapping))
        
        missing-required (filter (fn [field]
                                   (and (contains? (set required-fields) field)
                                        (not (contains? mapped-fields field))))
                                 required-fields)
        
        duplicate-mappings (let [field-counts (frequencies (vals mapping))]
                             (filter #(> (second %) 1) field-counts))
        
        issues (cond-> []
                 (seq missing-required)
                 (conj {:type :missing-required
                        :message (format "Required fields not mapped: %s"
                                         (str/join ", " missing-required))
                        :fields missing-required})
                 
                 (seq duplicate-mappings)
                 (conj {:type :duplicate-mappings
                        :message (format "Multiple headers mapped to same field: %s"
                                         (str/join ", " (map first duplicate-mappings)))
                        :duplicates duplicate-mappings}))]
    
    {:valid (empty? issues)
     :issues issues
     :stats {:total-headers (count (keys mapping))
             :mapped-fields (count mapped-fields)
             :missing-required (count missing-required)
             :duplicate-mappings (count duplicate-mappings)}}))

(comment
  ;; Test column mapping
  
  ;; Standard mapping
  (map-columns
    ["email" "first name" "last name" "job title"]
    ["Email Address" "FirstName" "Surname" "Position"])
  
  ;; Tricky headers
  (map-columns
    ["email" "first name" "last name" "mobile number"]
    ["E-Mail" "Given Name" "Family Name" "Cell Phone"])
  
  ;; Missing fields
  (map-columns
    ["email" "first name" "last name" "job title" "mobile number"]
    ["Email" "Full Name" "Department"])
  
  ;; Perfect match
  (map-columns
    ["email" "teams"]
    ["email" "teams"])
  
  ;; Validation
  (let [result (map-columns
                 ["email" "first name" "last name"]
                 ["Email" "FirstName" "Surname"])]
    (validate-mapping result ["email" "first name"])))