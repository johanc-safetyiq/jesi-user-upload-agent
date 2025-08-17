(ns user_upload.ai.sheet-detector
  "AI-powered detection of sheet structure in complex Excel files.
   
   This module uses Claude to identify which sheet contains user data
   and where the headers and data rows are located."
  (:require [user_upload.ai.claude :as claude]
            [user_upload.parser.document-analyzer :as analyzer]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]))

(defn load-sheet-detection-prompt
  "Load the sheet detection prompt template."
  []
  (try
    (slurp (io/resource "prompts/sheet-detection.txt"))
    (catch Exception e
      (log/error e "Failed to load sheet detection prompt")
      ;; Fallback prompt if file not found
      "Analyze the Excel file structure and identify which sheet contains user data.
       Return JSON with: sheet_name, header_row (0-indexed), data_start_row (0-indexed), confidence, reasoning.")))

(defn detect-user-data-sheet
  "Detect which sheet contains user data and where it starts.
   
   Args:
     excel-analysis - Analysis from document-analyzer/analyze-excel-file
   
   Returns:
     Map with keys:
       :success - Boolean indicating if detection succeeded
       :sheet-name - Name of sheet with user data
       :header-row - 0-based index of header row
       :data-start-row - 0-based index of first data row
       :confidence - Confidence level (high/medium/low)
       :reasoning - Explanation of detection
       :error - Error message (if failed)"
  [excel-analysis]
  (try
    (log/info "Detecting user data sheet using AI")
    
    (let [prompt-template (load-sheet-detection-prompt)
          analysis-text (analyzer/format-for-ai excel-analysis)
          full-prompt (str prompt-template "\n\nExcel file structure:\n" analysis-text)
          
          ;; Call Claude with the analysis
          response (claude/invoke-claude-with-timeout 
                   full-prompt
                   "Return ONLY valid JSON with the specified fields."
                   :allowed-tools "Read")]
      
      (if (:success response)
        (let [result (:result response)]
          (if (and (map? result)
                   (contains? result "sheet_name")
                   (contains? result "header_row")
                   (contains? result "data_start_row"))
            {:success true
             :sheet-name (get result "sheet_name")
             :header-row (get result "header_row")
             :data-start-row (get result "data_start_row")
             :confidence (or (get result "confidence") "medium")
             :reasoning (or (get result "reasoning") "AI analysis")}
            {:success false
             :error "Invalid response format from AI"
             :raw-response result}))
        {:success false
         :error (or (:error response) "AI detection failed")}))
    
    (catch Exception e
      (log/error e "Error detecting user data sheet")
      {:success false
       :error (.getMessage e)})))

(defn detect-from-file
  "Detect user data sheet directly from an Excel file.
   
   Args:
     file-path - Path to Excel file
   
   Returns:
     Same as detect-user-data-sheet"
  [file-path]
  (let [analysis (analyzer/analyze-excel-file file-path)]
    (if (:error analysis)
      {:success false
       :error (str "Failed to analyze file: " (:error analysis))}
      (detect-user-data-sheet analysis))))

(defn detect-from-bytes
  "Detect user data sheet from Excel bytes.
   
   Args:
     bytes - Excel file as byte array
     filename - Original filename
   
   Returns:
     Same as detect-user-data-sheet"
  [bytes filename]
  (let [analysis (analyzer/analyze-excel-bytes bytes filename)]
    (if (:error analysis)
      {:success false
       :error (str "Failed to analyze file: " (:error analysis))}
      (detect-user-data-sheet analysis))))

(comment
  ;; Test with problematic file
  (def result (detect-from-file "downloads/Copy of User  Team - Qbirt.xlsx"))
  (clojure.pprint/pprint result))