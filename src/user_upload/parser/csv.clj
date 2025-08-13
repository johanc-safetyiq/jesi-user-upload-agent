(ns user-upload.parser.csv
  "CSV file parsing with encoding detection and robust error handling"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.io FileInputStream InputStreamReader BufferedReader]
           [java.nio.charset Charset StandardCharsets]))

(def ^:private common-encodings
  "Common text encodings to try for CSV files"
  [StandardCharsets/UTF_8
   StandardCharsets/UTF_16
   StandardCharsets/UTF_16BE
   StandardCharsets/UTF_16LE
   StandardCharsets/ISO_8859_1
   (Charset/forName "windows-1252")])

(defn- try-read-with-encoding
  "Attempt to read first few lines with given encoding"
  [file-path encoding]
  (try
    (with-open [fis (FileInputStream. file-path)
                isr (InputStreamReader. fis encoding)
                br (BufferedReader. isr)]
      (let [lines (take 3 (line-seq br))]
        (when (seq lines)
          {:encoding encoding
           :sample-lines (vec lines)
           :success? true})))
    (catch Exception e
      {:encoding encoding
       :error (.getMessage e)
       :success? false})))

(defn detect-encoding
  "Detect the most likely encoding for a CSV file by trying common encodings"
  [file-path]
  (try
    (let [file (io/file file-path)]
      (when-not (.exists file)
        (throw (ex-info "File does not exist" {:file-path file-path})))
      (when-not (.canRead file)
        (throw (ex-info "File is not readable" {:file-path file-path})))
      
      (log/debug "Detecting encoding for file:" file-path)
      
      ;; Try each encoding and find the first one that works without errors
      (loop [encodings common-encodings]
        (if (empty? encodings)
          StandardCharsets/UTF_8  ; fallback to UTF-8
          (let [encoding (first encodings)
                result (try-read-with-encoding file-path encoding)]
            (if (:success? result)
              (do
                (log/debug "Detected encoding:" (.name encoding) "for file:" file-path)
                encoding)
              (recur (rest encodings)))))))
    (catch Exception e
      (log/warn e "Failed to detect encoding, defaulting to UTF-8 for file:" file-path)
      StandardCharsets/UTF_8)))

(defn parse-csv-with-encoding
  "Parse CSV file with specified encoding"
  [file-path encoding]
  (try
    (with-open [reader (io/reader file-path :encoding (.name encoding))]
      (doall (csv/read-csv reader)))
    (catch Exception e
      (log/error e "Failed to parse CSV with encoding" (.name encoding) "for file:" file-path)
      (throw (ex-info "Failed to parse CSV file"
                      {:file-path file-path
                       :encoding (.name encoding)
                       :error (.getMessage e)}
                      e)))))

(defn parse-csv-file
  "Parse CSV file with automatic encoding detection"
  [file-path]
  (try
    (log/info "Parsing CSV file:" file-path)
    (let [encoding (detect-encoding file-path)
          raw-data (parse-csv-with-encoding file-path encoding)]
      {:data raw-data
       :encoding (.name encoding)
       :row-count (count raw-data)})
    (catch Exception e
      (log/error e "Failed to parse CSV file:" file-path)
      (throw (ex-info "Failed to parse CSV file"
                      {:file-path file-path
                       :error (.getMessage e)}
                      e)))))

(defn normalize-cell-value
  "Normalize cell value by trimming whitespace and handling empty strings"
  [cell-value]
  (if (string? cell-value)
    (str/trim cell-value)
    (str cell-value)))

(defn csv-to-maps
  "Convert CSV data (vector of vectors) to vector of maps using first row as headers"
  [csv-data]
  (try
    (if (empty? csv-data)
      []
      (let [headers (mapv normalize-cell-value (first csv-data))
            data-rows (rest csv-data)]
        (mapv (fn [row]
                (zipmap headers
                        (mapv normalize-cell-value row)))
              data-rows)))
    (catch Exception e
      (log/error e "Failed to convert CSV data to maps")
      (throw (ex-info "Failed to convert CSV data to maps"
                      {:error (.getMessage e)}
                      e)))))

(defn filter-empty-rows
  "Remove rows that are completely empty or contain only empty strings"
  [rows]
  (filter (fn [row]
            (if (map? row)
              ;; For maps, check if any value is non-empty
              (some (fn [v] (and (string? v) (not (str/blank? v)))) (vals row))
              ;; For vectors, check if any cell is non-empty
              (some (fn [cell] (and (string? cell) (not (str/blank? cell)))) row)))
          rows))

(defn parse-csv-string
  "Parse CSV string into vector of vectors"
  [csv-string]
  (csv/read-csv (java.io.StringReader. csv-string)))

(defn read-csv-file
  "Read CSV file and return content with detected encoding"
  [file-path]
  (let [encoding (detect-encoding file-path)]
    {:encoding (.name encoding)
     :content (slurp file-path :encoding (.name encoding))}))

(defn parse-csv-to-maps
  "Parse CSV file directly to vector of maps with header keys"
  [file-path]
  (try
    (log/info "Parsing CSV file to maps:" file-path)
    (let [{:keys [data encoding]} (parse-csv-file file-path)
          maps-data (csv-to-maps data)
          filtered-data (filter-empty-rows maps-data)]
      {:data filtered-data
       :encoding encoding
       :total-rows (count data)
       :data-rows (count maps-data)
       :filtered-rows (count filtered-data)})
    (catch Exception e
      (log/error e "Failed to parse CSV to maps:" file-path)
      (throw (ex-info "Failed to parse CSV to maps"
                      {:file-path file-path
                       :error (.getMessage e)}
                      e)))))

(defn parse-csv-bytes
  "Parse CSV content from byte array"
  [bytes]
  (try
    (let [content (String. bytes "UTF-8")
          rows (parse-csv-string content)]
      (if (seq rows)
        (let [headers (map str/trim (first rows))
              data-rows (rest rows)]
          {:success true
           :data (map (fn [row]
                       (zipmap headers (map str/trim row)))
                     data-rows)
           :headers headers})
        {:success false
         :error "Empty CSV file"}))
    (catch Exception e
      (log/error e "Failed to parse CSV bytes")
      {:success false
       :error (.getMessage e)})))

(defn get-file-info
  "Get basic information about CSV file"
  [file-path]
  (try
    (let [file (io/file file-path)
          encoding (detect-encoding file-path)]
      (with-open [reader (io/reader file-path :encoding (.name encoding))]
        (let [first-line (first (line-seq reader))
              line-count (with-open [r (io/reader file-path :encoding (.name encoding))]
                          (count (line-seq r)))]
          {:file-path file-path
           :file-size (.length file)
           :encoding (.name encoding)
           :line-count line-count
           :first-line first-line
           :readable? (.canRead file)
           :exists? (.exists file)})))
    (catch Exception e
      (log/error e "Failed to get file info for:" file-path)
      {:file-path file-path
       :error (.getMessage e)
       :exists? false
       :readable? false})))

(defn validate-csv-structure
  "Basic validation of CSV file structure"
  [file-path]
  (try
    (let [{:keys [data encoding]} (parse-csv-file file-path)]
      (if (empty? data)
        {:valid? false
         :errors ["File is empty"]}
        (let [header-row (first data)
              data-rows (rest data)
              header-count (count header-row)
              row-length-issues (keep-indexed
                                 (fn [idx row]
                                   (when (not= (count row) header-count)
                                     {:row (inc idx)  ; 1-based for user display
                                      :expected header-count
                                      :actual (count row)}))
                                 data-rows)]
          {:valid? (empty? row-length-issues)
           :encoding encoding
           :total-rows (count data)
           :header-count header-count
           :headers (vec header-row)
           :errors (when (seq row-length-issues)
                     [(str "Inconsistent column counts in rows: "
                           (str/join ", " (map :row row-length-issues)))])})))
    (catch Exception e
      {:valid? false
       :error (.getMessage e)
       :errors ["Failed to validate CSV structure"]})))