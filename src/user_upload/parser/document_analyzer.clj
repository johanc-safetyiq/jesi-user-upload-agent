(ns user-upload.parser.document-analyzer
  "Analyzes Excel/CSV documents to extract structured summaries for AI processing.
   
   This module helps identify the correct sheet and row structure in complex spreadsheets
   by creating a machine-readable summary that can be analyzed by AI."
  (:require [dk.ative.docjure.spreadsheet :as xl]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn normalize-value
  "Normalize a cell value to a string representation."
  [value]
  (cond
    (nil? value) ""
    (string? value) (str/trim value)
    (number? value) (str value)
    :else (str value)))

(defn extract-sheet-rows
  "Extract raw rows from a worksheet.
   
   Args:
     worksheet - The worksheet to extract from
     max-rows - Maximum number of rows to extract (default 10)
   
   Returns:
     Vector of vectors containing cell values"
  [worksheet & {:keys [max-rows] :or {max-rows 10}}]
  (try
    (->> worksheet
         xl/row-seq
         (take max-rows)
         (mapv (fn [row]
                 (->> row
                      xl/cell-seq
                      (mapv (comp normalize-value xl/read-cell))))))
    (catch Exception e
      (log/error e "Failed to extract sheet rows")
      [])))

(defn count-sheet-rows
  "Count total number of non-empty rows in a worksheet."
  [worksheet]
  (try
    (->> worksheet
         xl/row-seq
         (filter (fn [row]
                  (some (fn [cell]
                          (not (str/blank? (normalize-value (xl/read-cell cell)))))
                        (xl/cell-seq row))))
         count)
    (catch Exception e
      (log/error e "Failed to count sheet rows")
      0)))


(defn analyze-sheet
  "Analyze a single worksheet to extract structure for AI processing.
   
   Args:
     worksheet - The worksheet to analyze
     sheet-name - Name of the sheet
     preview-rows - Number of rows to preview (default 10)
   
   Returns:
     Map with sheet preview data for AI analysis"
  [worksheet sheet-name & {:keys [preview-rows] :or {preview-rows 10}}]
  (let [rows (extract-sheet-rows worksheet :max-rows preview-rows)
        total-rows (count-sheet-rows worksheet)]
    
    {:sheet-name sheet-name
     :total-rows total-rows
     :preview-rows rows
     :max-columns (apply max 0 (map count rows))}))

(defn analyze-excel-file
  "Analyze an Excel file to extract document structure.
   
   Args:
     file-path - Path to the Excel file
   
   Returns:
     Map with complete document analysis"
  [file-path]
  (try
    (log/info "Analyzing Excel file structure:" file-path)
    (let [workbook (xl/load-workbook file-path)
          sheets (xl/sheet-seq workbook)
          sheet-names (mapv xl/sheet-name sheets)]
      
      {:file-path file-path
       :sheet-count (count sheets)
       :sheet-names sheet-names
       :sheets (mapv (fn [sheet sheet-name]
                      (analyze-sheet sheet sheet-name))
                    sheets sheet-names)
       :analysis-timestamp (java.time.Instant/now)})
    
    (catch Exception e
      (log/error e "Failed to analyze Excel file:" file-path)
      {:error (.getMessage e)
       :file-path file-path})))

(defn analyze-excel-bytes
  "Analyze Excel content from byte array.
   
   Args:
     bytes - Excel file as byte array
     filename - Original filename (for context)
   
   Returns:
     Map with complete document analysis"
  [bytes filename]
  (let [temp-file (java.io.File/createTempFile "excel-analysis-" ".xlsx")]
    (try
      (io/copy bytes temp-file)
      (assoc (analyze-excel-file (.getAbsolutePath temp-file))
             :original-filename filename)
      (finally
        (.delete temp-file)))))

(defn format-for-ai
  "Format document analysis for AI consumption.
   
   Args:
     analysis - Document analysis from analyze-excel-file
   
   Returns:
     String formatted for AI prompt"
  [analysis]
  (let [sb (StringBuilder.)]
    (.append sb (str "Excel File Analysis:\n"))
    (.append sb (str "Sheets: " (:sheet-count analysis) "\n\n"))
    
    (doseq [sheet (:sheets analysis)]
      (.append sb (str "Sheet: '" (:sheet-name sheet) "'\n"))
      (.append sb (str "Total Rows: " (:total-rows sheet) "\n"))
      (.append sb (str "Max Columns: " (:max-columns sheet) "\n"))
      
      (.append sb "\nFirst 10 rows:\n")
      (doseq [[idx row] (map-indexed vector (:preview-rows sheet))]
        (.append sb (str "Row " idx ": "))
        (.append sb (pr-str row))
        (.append sb "\n"))
      (.append sb "\n"))
    
    (.toString sb)))

(comment
  ;; Test with the problematic file
  (def analysis (analyze-excel-file "downloads/Copy of User  Team - Qbirt.xlsx"))
  
  ;; View the analysis
  (clojure.pprint/pprint analysis)
  
  ;; Format for AI
  (println (format-for-ai analysis)))