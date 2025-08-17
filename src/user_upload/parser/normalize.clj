(ns user_upload.parser.normalize
  "Header normalization and column name standardization"
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.tools.logging :as log]))

(def ^:private header-mappings
  "Standard header mappings for user upload CSV files"
  {"email" #{"email" "e-mail" "email address" "e-mail address" "user email" "login email"}
   "first name" #{"first name" "firstname" "first_name" "given name" "given_name" "fname"}
   "last name" #{"last name" "lastname" "last_name" "surname" "family name" "family_name" "lname"}
   "job title" #{"job title" "jobtitle" "job_title" "title" "position" "job role" "work title"}
   "mobile number" #{"mobile number" "mobile" "phone" "phone number" "cell phone" "mobile_number" "contact number"}
   "teams" #{"teams" "team" "team name" "team names" "group" "groups" "department" "departments"}
   "user role" #{"user role" "role" "user_role" "access role" "permission" "permissions" "user type" "account type"}})

(defn normalize-header
  "Normalize a single header string by trimming, lowercasing, and removing extra whitespace"
  [header]
  (when header
    (-> header
        str
        str/trim
        str/lower-case
        (str/replace #"\s+" " ")
        (str/replace #"[^\w\s-]" "")  ; Remove special chars except dash and underscore
        str/trim)))

(defn normalize-headers
  "Normalize a collection of headers, removing duplicates and empty values"
  [headers]
  (try
    (let [normalized (->> headers
                          (map normalize-header)
                          (remove str/blank?)
                          distinct
                          vec)]
      (log/debug "Headers normalized:" headers "->" normalized)
      normalized)
    (catch Exception e
      (log/error e "Failed to normalize headers:" headers)
      (throw (ex-info "Failed to normalize headers"
                      {:headers headers
                       :error (.getMessage e)}
                      e)))))

(defn find-standard-header
  "Find the standard header name for a given normalized header"
  [normalized-header]
  (some (fn [[standard-name variations]]
          (when (contains? variations normalized-header)
            standard-name))
        header-mappings))

(defn map-headers-to-standard
  "Map a collection of headers to their standard names"
  [headers]
  (try
    (let [normalized-headers (normalize-headers headers)
          mapping (reduce (fn [acc header]
                           (let [standard (find-standard-header header)]
                             (if standard
                               (assoc acc header standard)
                               (assoc acc header header))))
                         {}
                         normalized-headers)
          reverse-mapping (reduce (fn [acc [original standard]]
                                   (assoc acc standard original))
                                 {}
                                 mapping)]
      {:mapping mapping
       :reverse-mapping reverse-mapping
       :normalized-headers normalized-headers
       :unmapped-headers (filter #(= (mapping %) %) normalized-headers)})
    (catch Exception e
      (log/error e "Failed to map headers to standard:" headers)
      (throw (ex-info "Failed to map headers to standard"
                      {:headers headers
                       :error (.getMessage e)}
                      e)))))

(defn get-required-headers
  "Get the list of required headers for user upload"
  []
  (keys header-mappings))

(defn validate-required-headers
  "Check if all required headers are present in the mapped headers"
  [mapped-headers]
  (let [required (set (get-required-headers))
        present (set (keys mapped-headers))]
    {:missing-headers (vec (clojure.set/difference required present))
     :extra-headers (vec (clojure.set/difference present required))
     :valid? (empty? (clojure.set/difference required present))}))

(defn normalize-row-data
  "Normalize row data using header mapping"
  [row-data header-mapping]
  (try
    (reduce (fn [acc [original-header standard-header]]
              (let [value (get row-data original-header)]
                (assoc acc standard-header value)))
            {}
            header-mapping)
    (catch Exception e
      (log/error e "Failed to normalize row data")
      (throw (ex-info "Failed to normalize row data"
                      {:row-data row-data
                       :header-mapping header-mapping
                       :error (.getMessage e)}
                      e)))))

(defn normalize-dataset
  "Normalize an entire dataset (vector of maps) with header standardization"
  [dataset]
  (try
    (if (empty? dataset)
      {:data []
       :mapping {}
       :validation {:valid? true :missing-headers [] :extra-headers []}}
      
      (let [original-headers (keys (first dataset))
            header-analysis (map-headers-to-standard original-headers)
            validation (validate-required-headers (:reverse-mapping header-analysis))
            normalized-data (mapv #(normalize-row-data % (:mapping header-analysis)) dataset)]
        
        (log/info "Dataset normalized:"
                  "Original headers:" original-headers
                  "Mapped headers:" (:reverse-mapping header-analysis)
                  "Missing required:" (:missing-headers validation))
        
        {:data normalized-data
         :mapping (:mapping header-analysis)
         :reverse-mapping (:reverse-mapping header-analysis)
         :validation validation
         :original-headers original-headers
         :normalized-headers (:normalized-headers header-analysis)
         :unmapped-headers (:unmapped-headers header-analysis)}))
    
    (catch Exception e
      (log/error e "Failed to normalize dataset")
      (throw (ex-info "Failed to normalize dataset"
                      {:dataset-size (count dataset)
                       :first-row-keys (when (seq dataset) (keys (first dataset)))
                       :error (.getMessage e)}
                      e)))))

(defn suggest-header-mapping
  "Suggest possible header mappings for unmapped headers"
  [unmapped-headers]
  (mapv (fn [header]
          (let [suggestions (filter (fn [[_ variations]]
                                     (some #(str/includes? % header) variations))
                                   header-mappings)]
            {:original header
             :suggestions (mapv first suggestions)}))
        unmapped-headers))

(defn create-header-mapping
  "Create a header mapping from file headers to expected fields using heuristics"
  [file-headers expected-fields]
  (reduce (fn [mapping expected-field]
            (let [normalized-expected (normalize-header expected-field)
                  matching-header (first 
                                  (filter #(= (normalize-header %) normalized-expected) 
                                         file-headers))]
              (if matching-header
                (assoc mapping matching-header expected-field)
                mapping)))
          {}
          expected-fields))

(defn apply-header-mapping
  "Apply a header mapping to transform data rows"
  [data-rows header-mapping]
  (map (fn [row]
         (reduce (fn [new-row [old-key new-key]]
                  ;; Try to get value with both string and keyword versions of the key
                  (if-let [value (or (get row old-key)
                                    (get row (name old-key))  ; Convert keyword to string
                                    (get row (keyword old-key)))] ; Convert string to keyword
                    (assoc new-row new-key value)
                    new-row))
                {}
                header-mapping))
       data-rows))

(defn create-header-report
  "Create a detailed report of header normalization process"
  [normalization-result]
  (let [{:keys [original-headers normalized-headers mapping reverse-mapping
                validation unmapped-headers]} normalization-result
        suggestions (suggest-header-mapping unmapped-headers)]
    
    {:original-count (count original-headers)
     :normalized-count (count normalized-headers)
     :mapped-count (count reverse-mapping)
     :unmapped-count (count unmapped-headers)
     :required-missing (:missing-headers validation)
     :extra-headers (:extra-headers validation)
     :is-valid (:valid? validation)
     :header-mapping reverse-mapping
     :unmapped-headers unmapped-headers
     :suggestions suggestions
     :summary (str "Mapped " (count reverse-mapping) "/" (count original-headers) " headers. "
                   (if (:valid? validation)
                     "All required headers present."
                     (str "Missing: " (str/join ", " (:missing-headers validation)))))}))

(comment
  ;; Usage examples
  
  ;; Normalize headers
  (normalize-headers ["Email", "First Name ", "LAST_NAME", "Job-Title"])
  
  ;; Map to standard headers
  (map-headers-to-standard ["email", "first name", "surname", "mobile"])
  
  ;; Normalize dataset
  (normalize-dataset [{:email "john@example.com" :firstname "John" :surname "Doe"}
                      {:email "jane@example.com" :firstname "Jane" :surname "Smith"}]))