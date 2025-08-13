(ns exploration.ai.mapping-samples
  "Sample data and test cases for column mapping exploration.
   
   This file contains realistic sample headers from various organizations
   and file formats to test mapping accuracy in real-world scenarios."
  (:require [user-upload.ai.mapping :as mapping]
            [user-upload.ai.claude :as claude]
            [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]))

(comment
  "Real-world sample data for mapping tests.
   
   These samples are based on common patterns seen in actual
   user upload files from various organizations and systems."

  ;; HR System Exports - Common patterns from HR systems
  (def hr-system-samples
    [{:name "Workday Export"
      :headers ["Worker ID" "Legal Name - First" "Legal Name - Last" 
                "Primary Work Email" "Business Title" "Organization" 
                "Work Phone - Mobile" "Worker Type"]
      :note "Typical Workday HRIS export format"}
     
     {:name "BambooHR Export" 
      :headers ["Employee #" "First Name" "Last Name" "Work Email"
                "Job Title" "Department" "Mobile Phone Number" "Employee Status"]
      :note "BambooHR standard export"}
     
     {:name "ADP Export"
      :headers ["Associate ID" "First Name" "Last Name" "Company Mail"
                "Position Title" "Department Code" "Cell Phone" "Position Status"]
      :note "ADP Workforce export format"}])

  ;; Spreadsheet Templates - Common user-created formats
  (def spreadsheet-samples
    [{:name "Basic Template"
      :headers ["Name" "Email" "Role" "Team"]
      :note "Simple user-created template"}
     
     {:name "Detailed Template"
      :headers ["First Name" "Last Name" "Email Address" "Phone Number"
                "Job Title" "Department" "Manager" "Start Date" "Access Level"]
      :note "Comprehensive employee template"}
     
     {:name "IT Template"
      :headers ["Username" "Email" "First" "Last" "Dept" "Role" "Phone"]
      :note "IT-focused template with abbreviated headers"}])

  ;; CSV Exports - Various system exports  
  (def csv-export-samples
    [{:name "Active Directory Export"
      :headers ["sAMAccountName" "givenName" "sn" "mail" "title" 
                "department" "mobile" "userAccountControl"]
      :note "Active Directory user export"}
     
     {:name "Google Workspace"
      :headers ["Email Address [Required]" "First Name [Required]" "Last Name [Required]"
                "Password [Required]" "Org Unit Path [Required]" "Phone [Work]"]
      :note "Google Admin Console bulk upload format"}
     
     {:name "Office 365 Export"
      :headers ["UserPrincipalName" "DisplayName" "GivenName" "Surname" 
                "JobTitle" "Department" "MobilePhone" "UsageLocation"]
      :note "Office 365 user export"}])

  ;; International Variations - Different languages and formats
  (def international-samples
    [{:name "German Format"
      :headers ["Vorname" "Nachname" "E-Mail" "Telefon" "Position" "Abteilung"]
      :note "German language headers"}
     
     {:name "French Format"
      :headers ["Prénom" "Nom de famille" "Courriel" "Téléphone" "Poste" "Département"]
      :note "French language headers"}
     
     {:name "Spanish Format"
      :headers ["Nombre" "Apellido" "Correo Electrónico" "Teléfono" "Cargo" "Departamento"]
      :note "Spanish language headers"}])

  ;; Problematic Cases - Common issues in real files
  (def problematic-samples
    [{:name "Mixed Case Inconsistency"
      :headers ["EMAIL" "firstName" "Last_Name" "jobTITLE" "PHONE_NUMBER"]
      :note "Inconsistent capitalization patterns"}
     
     {:name "Special Characters"
      :headers ["E-mail@Address" "First-Name" "Last_Name#" "Job/Title" "Phone (Mobile)"]
      :note "Headers with special characters"}
     
     {:name "Very Long Headers"
      :headers ["Primary_Business_Email_Address_Field" "Employee_First_Given_Name"
                "Employee_Last_Family_Name" "Current_Job_Position_Title"]
      :note "Overly verbose header names"}
     
     {:name "Ambiguous Headers"
      :headers ["ID" "Name" "Contact" "Role" "Group"]
      :note "Generic headers that could mean multiple things"}])

  ;; Test all sample data with heuristic mapping
  (defn test-all-samples-heuristic []
    (println "\n=== Testing All Sample Data (Heuristic) ===\n")
    
    (let [all-samples (concat hr-system-samples spreadsheet-samples 
                              csv-export-samples international-samples 
                              problematic-samples)
          expected-fields ["email" "first name" "last name" "job title" 
                           "mobile number" "teams" "user role"]]
      
      (doseq [{:keys [name headers note]} all-samples]
        (println (format "Testing: %s" name))
        (println (format "  Note: %s" note))
        (println (format "  Headers (%d): %s" (count headers) headers))
        
        (let [result (mapping/heuristic-column-mapping expected-fields headers)
              mapped-count (count (:mapping result))
              total-expected (count expected-fields)
              coverage-pct (* 100.0 (/ mapped-count total-expected))]
          
          (println (format "  Mapping (%d): %s" mapped-count (:mapping result)))
          (println (format "  Unmapped: %s" (:unmapped result)))
          (println (format "  Coverage: %.1f%% (Confidence: %s)" 
                           coverage-pct (:confidence result)))
          
          ;; Highlight perfect or poor mappings
          (cond
            (= coverage-pct 100.0) (println "  🎯 Perfect mapping!")
            (< coverage-pct 30.0) (println "  ⚠️ Poor mapping coverage")
            (< coverage-pct 60.0) (println "  📊 Moderate mapping"))
          
          (println)))))

  ;; Test samples with AI (limited due to API usage)
  (defn test-samples-ai-vs-heuristic []
    (println "\n=== AI vs Heuristic Sample Comparison ===\n")
    
    (let [ai-availability (claude/check-claude-availability)]
      
      (if (:available ai-availability)
        (do
          (println "✅ Claude available, testing sample AI mappings...\n")
          
          ;; Test a subset of samples to conserve API usage
          (let [test-samples (take 3 (concat hr-system-samples spreadsheet-samples))
                expected-fields ["email" "first name" "last name" "job title" "mobile number"]]
            
            (doseq [{:keys [name headers note]} test-samples]
              (println (format "Comparing: %s" name))
              (println (format "  Headers: %s" headers))
              
              (let [heuristic-result (mapping/heuristic-column-mapping expected-fields headers)
                    ai-result (mapping/ai-column-mapping expected-fields headers)
                    
                    heuristic-coverage (* 100.0 (/ (count (:mapping heuristic-result)) 
                                                    (count expected-fields)))
                    ai-coverage (if (:success ai-result)
                                  (* 100.0 (/ (count (:mapping ai-result)) 
                                              (count expected-fields)))
                                  0.0)]
                
                (println (format "  Heuristic: %.1f%% coverage (Confidence: %s)" 
                                 heuristic-coverage (:confidence heuristic-result)))
                
                (if (:success ai-result)
                  (do
                    (println (format "  AI: %.1f%% coverage" ai-coverage))
                    
                    ;; Compare specific mappings
                    (let [h-mapping (:mapping heuristic-result)
                          ai-mapping (:mapping ai-result)
                          agreements (count (filter (fn [[header field]]
                                                      (= field (get h-mapping header)))
                                                    ai-mapping))]
                      (println (format "  Agreement: %d/%d mappings match" 
                                       agreements (min (count h-mapping) (count ai-mapping))))
                      
                      ;; Show differences
                      (when (not= h-mapping ai-mapping)
                        (println "  Differences:")
                        (doseq [[header h-field] h-mapping]
                          (let [ai-field (get ai-mapping header)]
                            (when (and ai-field (not= h-field ai-field))
                              (println (format "    '%s': Heuristic→%s, AI→%s" 
                                               header h-field ai-field))))))))
                  (println (format "  AI failed: %s" (:error ai-result))))
                
                (println)))))
        
        (println "❌ Claude not available, cannot run AI comparison"))))

  ;; Analyze mapping patterns across samples
  (defn analyze-mapping-patterns []
    (println "\n=== Mapping Pattern Analysis ===\n")
    
    (let [all-samples (concat hr-system-samples spreadsheet-samples csv-export-samples)
          expected-fields ["email" "first name" "last name" "job title" "mobile number" "teams"]
          
          ;; Run heuristic mapping on all samples
          results (map (fn [sample]
                         (let [result (mapping/heuristic-column-mapping 
                                       expected-fields (:headers sample))]
                           (assoc sample :mapping-result result)))
                       all-samples)]
      
      ;; Analyze coverage statistics
      (let [coverages (map (fn [{:keys [mapping-result]}]
                             (* 100.0 (/ (count (:mapping mapping-result))
                                         (count expected-fields))))
                           results)]
        
        (println "Coverage Statistics:")
        (println (format "  Average: %.1f%%" (/ (reduce + coverages) (count coverages))))
        (println (format "  Best: %.1f%%" (apply max coverages)))
        (println (format "  Worst: %.1f%%" (apply min coverages)))
        (println (format "  Perfect mappings: %d/%d" 
                         (count (filter #(= 100.0 %) coverages)) 
                         (count coverages))))
      
      ;; Analyze most commonly mapped fields
      (let [all-mappings (apply merge (map #(get-in % [:mapping-result :mapping]) results))
            field-counts (frequencies (vals all-mappings))]
        
        (println "\nMost Successfully Mapped Fields:")
        (doseq [[field count] (sort-by second > field-counts)]
          (println (format "  %s: %d/%d samples (%.1f%%)" 
                           field count (count results) 
                           (* 100.0 (/ count (count results)))))))
      
      ;; Analyze most problematic headers
      (let [all-headers (mapcat :headers all-samples)
            unmapped-headers (mapcat (fn [{:keys [headers mapping-result]}]
                                       (let [mapped-headers (set (keys (:mapping mapping-result)))]
                                         (remove mapped-headers headers)))
                                     results)
            problem-counts (frequencies unmapped-headers)]
        
        (when (seq problem-counts)
          (println "\nMost Problematic Headers (frequently unmapped):")
          (doseq [[header count] (take 5 (sort-by second > problem-counts))]
            (println (format "  '%s': failed in %d samples" header count)))))))

  ;; Test edge cases with sample data
  (defn test-edge-cases-with-samples []
    (println "\n=== Edge Cases with Sample Data ===\n")
    
    ;; Test with no expected fields
    (let [result (mapping/map-columns [] (first (:headers hr-system-samples)))]
      (println "Empty expected fields:")
      (println (format "  Result: %s" result)))
    
    ;; Test with duplicate headers
    (let [duplicate-headers ["Email" "Email" "Name" "Name" "Role"]
          result (mapping/map-columns ["email" "first name" "user role"] duplicate-headers)]
      (println "\nDuplicate headers:")
      (println (format "  Mapping: %s" (:mapping result))))
    
    ;; Test with very long header list
    (let [long-headers (take 50 (cycle ["Email" "Name" "Phone" "Title" "Dept"]))
          result (mapping/map-columns ["email" "first name"] long-headers)]
      (println "\nVery long header list (50 columns):")
      (println (format "  Mapped %d fields from %d headers" 
                       (count (:mapping result)) (count long-headers))))
    
    ;; Test with unicode characters
    (let [unicode-headers ["Ëmáìl@Àddrëss" "Fïrst-Nämé" "Làst_Nàmë"]
          result (mapping/map-columns ["email" "first name" "last name"] unicode-headers)]
      (println "\nUnicode headers:")
      (println (format "  Mapping: %s" (:mapping result)))))

  ;; Generate mapping quality report
  (defn generate-mapping-report []
    (println "\n🔍 COLUMN MAPPING QUALITY REPORT")
    (println "=" * 50)
    
    (println "\n1. Testing Standard Sample Data...")
    (test-all-samples-heuristic)
    
    (println "\n2. AI vs Heuristic Comparison...")
    (test-samples-ai-vs-heuristic)
    
    (println "\n3. Pattern Analysis...")
    (analyze-mapping-patterns)
    
    (println "\n4. Edge Case Testing...")
    (test-edge-cases-with-samples)
    
    (println "\n✅ Mapping quality report completed"))

  ;; Quick test specific samples
  (defn test-specific-samples []
    (println "\n=== Quick Sample Tests ===\n")
    
    ;; Test a few interesting cases
    (let [interesting-cases
          [{:name "Google Workspace" 
            :headers ["Email Address [Required]" "First Name [Required]" "Last Name [Required]"]}
           {:name "Active Directory"
            :headers ["mail" "givenName" "sn" "title" "department"]}
           {:name "Simple Template"
            :headers ["Name" "Email" "Role"]}]]
      
      (doseq [{:keys [name headers]} interesting-cases]
        (let [result (mapping/map-columns 
                      ["email" "first name" "last name" "job title"] headers)]
          (println (format "%s mapping:" name))
          (println (format "  %s -> %s" headers (:mapping result)))
          (println (format "  Unmapped: %s" (:unmapped result)))
          (println))))))