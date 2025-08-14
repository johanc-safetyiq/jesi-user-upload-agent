(ns user-upload.parser.excel
  "Excel file parsing using docjure library"
  (:require [dk.ative.docjure.spreadsheet :as xl]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn detect-encoding
  "Detect file encoding. Excel files are binary so encoding detection is limited.
   Returns :binary for Excel files, :utf-8 as default for others."
  [file-path]
  (let [file (io/file file-path)
        name (.getName file)
        extension (when-let [dot-idx (.lastIndexOf name ".")]
                   (.toLowerCase (.substring name (inc dot-idx))))]
    (case extension
      ("xlsx" "xls") :binary
      :utf-8)))

(defn load-workbook
  "Load Excel workbook from file path with error handling"
  [file-path]
  (try
    (let [file (io/file file-path)]
      (when-not (.exists file)
        (throw (ex-info "File does not exist" {:file-path file-path})))
      (when-not (.canRead file)
        (throw (ex-info "File is not readable" {:file-path file-path})))
      (xl/load-workbook file-path))
    (catch Exception e
      (log/error e "Failed to load Excel workbook:" file-path)
      (throw (ex-info "Failed to load Excel workbook"
                      {:file-path file-path
                       :error (.getMessage e)}
                      e)))))

(defn sheet-names
  "Get all sheet names from workbook"
  [workbook]
  (try
    (->> workbook
         xl/sheet-seq
         (map xl/sheet-name)
         vec)
    (catch Exception e
      (log/error e "Failed to get sheet names")
      (throw (ex-info "Failed to get sheet names"
                      {:error (.getMessage e)}
                      e)))))

(defn select-sheet
  "Select sheet by name or index from workbook"
  [workbook sheet-identifier]
  (try
    (cond
      (string? sheet-identifier)
      (xl/select-sheet sheet-identifier workbook)
      
      (number? sheet-identifier)
      (let [sheets (xl/sheet-seq workbook)]
        (nth sheets sheet-identifier nil))
      
      :else
      (throw (ex-info "Invalid sheet identifier" {:identifier sheet-identifier})))
    (catch Exception e
      (log/error e "Failed to select sheet:" sheet-identifier)
      (throw (ex-info "Failed to select sheet"
                      {:sheet-identifier sheet-identifier
                       :error (.getMessage e)}
                      e)))))

(defn sheet-data
  "Extract data from worksheet as vector of vectors"
  [worksheet]
  (try
    (->> worksheet
         (xl/row-seq)
         (map (fn [row]
                (->> row
                     xl/cell-seq
                     (map xl/read-cell)
                     vec)))
         vec)
    (catch Exception e
      (log/error e "Failed to extract sheet data")
      (throw (ex-info "Failed to extract sheet data"
                      {:error (.getMessage e)}
                      e)))))

(defn normalize-cell-value
  "Normalize cell value to string, handling various data types"
  [cell-value]
  (cond
    (nil? cell-value) ""
    (string? cell-value) (clojure.string/trim cell-value)
    (number? cell-value) (str cell-value)
    (instance? java.util.Date cell-value) (str cell-value)
    (instance? java.time.LocalDateTime cell-value) (str cell-value)
    :else (str cell-value)))

(defn parse-sheet
  "Parse a single sheet into maps with header keys"
  [worksheet]
  (try
    (let [raw-data (sheet-data worksheet)]
      (if (empty? raw-data)
        []
        (let [headers (mapv normalize-cell-value (first raw-data))
              data-rows (rest raw-data)
              ;; Filter out completely empty rows
              non-empty-rows (filter (fn [row]
                                      ;; Check if at least one cell has content
                                      (some #(and % (not= "" (normalize-cell-value %))) row))
                                    data-rows)]
          (mapv (fn [row]
                  (zipmap headers
                          (mapv normalize-cell-value row)))
                non-empty-rows))))
    (catch Exception e
      (log/error e "Failed to parse sheet")
      (throw (ex-info "Failed to parse sheet"
                      {:error (.getMessage e)}
                      e)))))

(defn parse-excel-file
  "Parse Excel file, returning data from first sheet or specified sheet"
  ([file-path]
   (parse-excel-file file-path 0))
  ([file-path sheet-identifier]
   (try
     (log/info "Parsing Excel file:" file-path "sheet:" sheet-identifier)
     (let [workbook (load-workbook file-path)
           worksheet (select-sheet workbook sheet-identifier)]
       (if worksheet
         (parse-sheet worksheet)
         (throw (ex-info "Sheet not found"
                         {:file-path file-path
                          :sheet-identifier sheet-identifier}))))
     (catch Exception e
       (log/error e "Failed to parse Excel file:" file-path)
       (throw (ex-info "Failed to parse Excel file"
                       {:file-path file-path
                        :sheet-identifier sheet-identifier
                        :error (.getMessage e)}
                       e))))))

(defn parse-all-sheets
  "Parse all sheets in Excel file, returning map of sheet-name -> data"
  [file-path]
  (try
    (log/info "Parsing all sheets from Excel file:" file-path)
    (let [workbook (load-workbook file-path)
          sheet-names (sheet-names workbook)]
      (reduce (fn [result sheet-name]
                (let [worksheet (select-sheet workbook sheet-name)
                      data (parse-sheet worksheet)]
                  (assoc result sheet-name data)))
              {}
              sheet-names))
    (catch Exception e
      (log/error e "Failed to parse all sheets from Excel file:" file-path)
      (throw (ex-info "Failed to parse all sheets"
                      {:file-path file-path
                       :error (.getMessage e)}
                      e)))))

(defn parse-excel-bytes
  "Parse Excel content from byte array.
   Creates a temporary file since docjure requires file path."
  [bytes]
  (let [temp-file (java.io.File/createTempFile "excel-" ".xlsx")]
    (try
      (clojure.java.io/copy bytes temp-file)
      (let [result (parse-excel-file (.getAbsolutePath temp-file))]
        {:success true
         :data result
         :headers (when (seq result)
                   (keys (first result)))})
      (catch Exception e
        (log/error e "Failed to parse Excel bytes")
        {:success false
         :error (.getMessage e)})
      (finally
        (.delete temp-file)))))

(defn get-file-info
  "Get basic information about Excel file"
  [file-path]
  (try
    (let [file (io/file file-path)
          workbook (load-workbook file-path)
          sheets (sheet-names workbook)]
      {:file-path file-path
       :file-size (.length file)
       :encoding (detect-encoding file-path)
       :sheet-count (count sheets)
       :sheet-names sheets
       :readable? (.canRead file)
       :exists? (.exists file)})
    (catch Exception e
      (log/error e "Failed to get file info for:" file-path)
      {:file-path file-path
       :error (.getMessage e)
       :exists? false
       :readable? false})))