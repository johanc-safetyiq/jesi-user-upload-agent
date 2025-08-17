(ns user_upload.parser.validate
  "Data validation for user upload CSV files"
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.set :as set]))

(def ^:private valid-user-roles
  "Valid user roles for the system (case-insensitive)"
  #{"TEAM MEMBER" "MANAGER" "MONITOR" "ADMINISTRATOR" "COMPANY ADMINISTRATOR"})

(def ^:private email-regex
  "Regular expression for basic email validation"
  #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$")

(defn validate-email
  "Validate email format"
  [email]
  (let [trimmed-email (str/trim (str email))]
    (cond
      (str/blank? trimmed-email)
      {:valid? false :error "Email is required"}
      
      (not (re-matches email-regex trimmed-email))
      {:valid? false :error "Invalid email format"}
      
      :else
      {:valid? true :normalized-value trimmed-email})))

(defn validate-required-field
  "Validate that a required field is not blank"
  [field-name field-value]
  (let [trimmed-value (str/trim (str field-value))]
    (if (str/blank? trimmed-value)
      {:valid? false :error (str field-name " is required")}
      {:valid? true :normalized-value trimmed-value})))

(defn validate-user-role
  "Validate user role against allowed values (case-insensitive)"
  [role]
  (let [trimmed-role (str/trim (str role))
        upper-role (str/upper-case trimmed-role)]
    (cond
      (str/blank? trimmed-role)
      {:valid? false :error "User role is required"}
      
      (not (contains? valid-user-roles upper-role))
      {:valid? false 
       :error (str "Invalid user role: " trimmed-role 
                   ". Valid roles: " (str/join ", " valid-user-roles))}
      
      :else
      {:valid? true :normalized-value upper-role})))

(defn parse-teams
  "Parse teams string separated by | character"
  [teams-str]
  (try
    (if (str/blank? teams-str)
      {:valid? false :error "Teams field is required"}
      (let [teams (->> (str/split (str teams-str) #"\|")
                      (map str/trim)
                      (remove str/blank?)
                      distinct
                      vec)]
        (if (empty? teams)
          {:valid? false :error "At least one team must be specified"}
          {:valid? true :normalized-value teams})))
    (catch Exception e
      {:valid? false :error (str "Failed to parse teams: " (.getMessage e))})))

(defn validate-mobile-number
  "Validate mobile number, defaulting to '0' if empty"
  [mobile]
  (let [trimmed-mobile (str/trim (str mobile))]
    (if (str/blank? trimmed-mobile)
      {:valid? true :normalized-value "0"}
      {:valid? true :normalized-value trimmed-mobile})))

(defn validate-job-title
  "Validate job title - can be empty"
  [job-title]
  (let [trimmed-title (str/trim (str job-title))]
    {:valid? true :normalized-value (if (str/blank? trimmed-title) "" trimmed-title)}))

(defn validate-row
  "Validate a single row of user data"
  [row row-number]
  (try
    (let [;; Support both keyword and string keys, with various formats
          get-field (fn [row & keys]
                      (some #(or (get row %) 
                                (get row (keyword %))
                                (get row (str/lower-case %)))
                           keys))
          
          validations {:email (validate-email (get-field row "email" "Email"))
                      :first-name (validate-required-field "First name" 
                                                          (get-field row "first name" "First Name" "first-name"))
                      :last-name (validate-required-field "Last name" 
                                                         (get-field row "last name" "Last Name" "last-name"))
                      :job-title (validate-job-title (get-field row "job title" "Job Title" "job-title"))
                      :mobile-number (validate-mobile-number (get-field row "mobile number" "Mobile Number" "mobile-number"))
                      :teams (parse-teams (get-field row "teams" "Teams"))
                      :user-role (validate-user-role (get-field row "user role" "USER Role" "User Role" "user-role"))}
          
          errors (keep (fn [[field validation]]
                        (when-not (:valid? validation)
                          {:field field :error (:error validation)}))
                      validations)
          
          normalized-data (reduce (fn [acc [field validation]]
                                   (if (:valid? validation)
                                     (assoc acc field (:normalized-value validation))
                                     acc))
                                 {}
                                 validations)]
      
      {:row-number row-number
       :valid? (empty? errors)
       :errors errors
       :normalized-data normalized-data
       :original-data row})
    
    (catch Exception e
      (log/error e "Failed to validate row" row-number)
      {:row-number row-number
       :valid? false
       :errors [{:field "general" :error (.getMessage e)}]
       :original-data row})))

(defn check-email-uniqueness
  "Check for duplicate emails within the dataset"
  [validated-rows]
  (try
    (let [emails (keep (fn [row]
                        (when (:valid? row)
                          (get-in row [:normalized-data :email])))
                      validated-rows)
          email-counts (frequencies emails)
          duplicates (filter #(> (second %) 1) email-counts)]
      
      (if (empty? duplicates)
        {:unique? true :duplicates []}
        {:unique? false 
         :duplicates (mapv (fn [[email count]]
                            {:email email :count count})
                          duplicates)}))
    (catch Exception e
      (log/error e "Failed to check email uniqueness")
      {:unique? false :error (.getMessage e)})))

(defn mark-duplicate-emails
  "Mark rows with duplicate emails as invalid"
  [validated-rows uniqueness-check]
  (if (:unique? uniqueness-check)
    validated-rows
    (let [duplicate-emails (set (map :email (:duplicates uniqueness-check)))]
      (mapv (fn [row]
              (if (and (:valid? row)
                      (contains? duplicate-emails (get-in row [:normalized-data :email])))
                (-> row
                    (assoc :valid? false)
                    (update :errors conj {:field "email" :error "Duplicate email address"}))
                row))
            validated-rows))))

(defn validate-dataset
  "Validate an entire dataset of user records"
  [dataset]
  (try
    (log/info "Validating dataset with" (count dataset) "rows")
    
    (if (empty? dataset)
      {:valid? true
       :total-rows 0
       :valid-rows 0
       :invalid-rows 0
       :rows []
       :email-uniqueness {:unique? true :duplicates []}
       :summary "Empty dataset"}
      
      (let [;; Validate each row individually
            validated-rows (mapv #(validate-row %1 %2) dataset (range 1 (inc (count dataset))))
            
            ;; Check email uniqueness
            uniqueness-check (check-email-uniqueness validated-rows)
            
            ;; Mark duplicate emails as invalid
            final-rows (mark-duplicate-emails validated-rows uniqueness-check)
            
            ;; Calculate statistics
            valid-count (count (filter :valid? final-rows))
            invalid-count (- (count final-rows) valid-count)
            
            ;; Extract error summary
            all-errors (mapcat :errors (filter #(not (:valid? %)) final-rows))
            error-summary (frequencies (map :error all-errors))]
        
        (log/info "Validation complete:"
                  "Total:" (count final-rows)
                  "Valid:" valid-count
                  "Invalid:" invalid-count)
        
        ;; Log sample of validation errors for debugging
        (when (> invalid-count 0)
          (let [sample-errors (take 3 (filter #(not (:valid? %)) final-rows))]
            (log/warn "Sample validation errors:")
            (doseq [row sample-errors]
              (log/warn (str "  Row " (:row-number row) ": " 
                           (str/join ", " (map #(str (:field %) " - " (:error %)) 
                                             (:errors row))))))))
        
        {:valid? (= invalid-count 0)
         :total-rows (count final-rows)
         :valid-rows valid-count
         :invalid-rows invalid-count
         :rows final-rows
         :email-uniqueness uniqueness-check
         :error-summary error-summary
         :summary (str "Validated " (count final-rows) " rows. "
                      valid-count " valid, " invalid-count " invalid.")}))
    
    (catch Exception e
      (log/error e "Failed to validate dataset")
      {:valid? false
       :error (.getMessage e)
       :total-rows (count dataset)
       :summary "Validation failed"})))

(defn get-valid-rows
  "Extract only valid rows with normalized data"
  [validation-result]
  (->> (:rows validation-result)
       (filter :valid?)
       (mapv :normalized-data)))

(defn get-invalid-rows
  "Extract only invalid rows with their errors"
  [validation-result]
  (->> (:rows validation-result)
       (filter #(not (:valid? %)))
       (mapv #(select-keys % [:row-number :errors :original-data]))))

(defn create-validation-report
  "Create a detailed validation report"
  [validation-result]
  (let [{:keys [total-rows valid-rows invalid-rows email-uniqueness error-summary]} validation-result]
    {:statistics {:total-rows total-rows
                  :valid-rows valid-rows
                  :invalid-rows invalid-rows
                  :success-rate (if (> total-rows 0)
                                 (double (/ valid-rows total-rows))
                                 0.0)}
     :email-validation {:unique? (:unique? email-uniqueness)
                        :duplicate-count (count (:duplicates email-uniqueness))
                        :duplicates (:duplicates email-uniqueness)}
     :error-breakdown error-summary
     :invalid-rows (get-invalid-rows validation-result)
     :summary (:summary validation-result)}))

(defn get-validation-summary
  "Get a concise validation summary"
  [validation-result]
  (let [report (create-validation-report validation-result)]
    (str "Validation Results: "
         (get-in report [:statistics :valid-rows]) " valid, "
         (get-in report [:statistics :invalid-rows]) " invalid out of "
         (get-in report [:statistics :total-rows]) " total rows"
         (when-not (get-in report [:email-validation :unique?])
           (str " (Warning: " (get-in report [:email-validation :duplicate-count]) " duplicate emails)")))))

(comment
  ;; Usage examples
  
  ;; Validate individual fields
  (validate-email "john@example.com")
  (validate-user-role "team member")
  (parse-teams "Engineering|Development|QA")
  
  ;; Validate a single row
  (validate-row {"email" "john@example.com"
                 "first name" "John"
                 "last name" "Doe"
                 "job title" "Engineer"
                 "mobile number" ""
                 "teams" "Engineering|Development"
                 "user role" "TEAM MEMBER"} 1)
  
  ;; Validate entire dataset
  (validate-dataset [{"email" "john@example.com"
                      "first name" "John"
                      "last name" "Doe"
                      "job title" "Engineer"
                      "mobile number" ""
                      "teams" "Engineering"
                      "user role" "TEAM MEMBER"}]))