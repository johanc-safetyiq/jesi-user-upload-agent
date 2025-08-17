(ns fetch-jesi-7693
  "Simple script to fetch JESI-7693 details and download CSV attachment."
  (:require
    [user-upload.jira.client :as jira]
    [user-upload.config :as config]
    [clojure.string :as str]
    [clj-http.client :as http]
    [clojure.java.io :as io]
    [clojure.data.csv :as csv]))

(defn extract-comment-text
  "Extract plain text from ADF comment body structure."
  [comment-body]
  (letfn [(extract-text [node]
            (cond
              (and (map? node) (:text node))
              (:text node)
              
              (and (map? node) (:content node))
              (str/join "" (map extract-text (:content node)))
              
              (sequential? node)
              (str/join "" (map extract-text node))
              
              :else ""))]
    (extract-text comment-body)))

(defn download-attachment
  "Download an attachment from Jira."
  [attachment-url filename]
  (println (format "Downloading: %s" filename))
  (try
    (let [cfg (config/config)
          email (get-in cfg [:jira :email])
          api-token (get-in cfg [:jira :api-token])
          auth-header (str "Basic " 
                          (.encodeToString (java.util.Base64/getEncoder)
                                         (.getBytes (str email ":" api-token) "UTF-8")))
          response (http/get attachment-url 
                           {:headers {"Authorization" auth-header}
                            :as :byte-array
                            :throw-exceptions false})]
      (if (< (:status response) 400)
        (do
          (println (format "✓ Downloaded %s (%d bytes)" filename (count (:body response))))
          {:success true :content (:body response) :size (count (:body response))})
        (do
          (println (format "✗ Failed: HTTP %d" (:status response)))
          {:success false :error (format "HTTP %d" (:status response))})))
    (catch Exception e
      (println (format "✗ Exception: %s" (.getMessage e)))
      {:success false :error (.getMessage e)})))

(defn save-csv-file
  "Save CSV content to downloads folder."
  [content filename]
  (let [output-path (str "downloads/" filename)]
    (io/make-parents output-path)
    (with-open [out (io/output-stream output-path)]
      (.write out content))
    (println (format "✓ Saved to: %s" output-path))
    output-path))

(defn analyze-csv
  "Analyze CSV content for team disambiguation."
  [csv-content]
  (try
    (let [rows (csv/read-csv csv-content)
          headers (first rows)
          data-rows (rest rows)
          team-col-idx (first (keep-indexed #(when (str/includes? (str/lower-case %2) "team") %1) headers))
          has-disambiguation? (and team-col-idx
                                  (some #(str/includes? (nth % team-col-idx "") "|") data-rows))]
      {:success true
       :headers headers
       :row-count (count data-rows)
       :has-team-disambiguation has-disambiguation?
       :sample-rows (take 3 data-rows)
       :team-column-index team-col-idx})
    (catch Exception e
      {:success false :error (.getMessage e)})))

(defn fetch-jesi-7693
  "Fetch JESI-7693 details and download CSV."
  []
  (println "=== Fetching JESI-7693 ===")
  
  (try
    ;; Fetch issue with attachments and comments
    (let [issue (jira/get-issue-with-attachments "JESI-7693")
          fields (:fields issue)
          status (get-in fields [:status :name])
          summary (:summary fields)
          attachments (:attachment fields)
          comments (get-in fields [:comment :comments])]
      
      (println (format "✓ Issue fetched: %s" (:key issue)))
      (println (format "  Summary: %s" summary))
      (println (format "  Status: %s" status))
      (println (format "  Attachments: %d" (count attachments)))
      (println (format "  Comments: %d" (count comments)))
      
      ;; Show attachments
      (println "\n--- Attachments ---")
      (doseq [att attachments]
        (println (format "  • %s (%s bytes)" (:filename att) (:size att))))
      
      ;; Show recent comments with approval focus
      (println "\n--- Recent Comments ---")
      (let [recent-comments (take-last 3 comments)]
        (doseq [comment recent-comments]
          (let [author (get-in comment [:author :displayName])
                created (:created comment)
                text (extract-comment-text (:body comment))]
            (println (format "  • %s (%s)" author created))
            (if (> (count text) 150)
              (println (format "    %s..." (subs text 0 150)))
              (println (format "    %s" text)))
            (when (str/includes? (str/lower-case text) "approval")
              (println "    *** APPROVAL RELATED ***")))))
      
      ;; Find and download CSV
      (println "\n--- Looking for CSV ---")
      (let [csv-attachment (first (filter #(str/includes? (:filename %) "users-for-approval.csv") attachments))]
        (if csv-attachment
          (do
            (println (format "✓ Found: %s" (:filename csv-attachment)))
            (let [download-result (download-attachment (:content csv-attachment) (:filename csv-attachment))]
              (if (:success download-result)
                (let [content-str (String. (:content download-result) "UTF-8")
                      csv-analysis (analyze-csv content-str)]
                  (println "\n--- CSV Analysis ---")
                  (if (:success csv-analysis)
                    (do
                      (println (format "  Headers: %s" (str/join ", " (:headers csv-analysis))))
                      (println (format "  Rows: %d" (:row-count csv-analysis)))
                      (println (format "  Team disambiguation: %s" (:has-team-disambiguation csv-analysis)))
                      (when (:has-team-disambiguation csv-analysis)
                        (println "  *** TEAM DISAMBIGUATION DETECTED ***"))
                      
                      (println "\n--- Sample Data ---")
                      (doseq [[idx row] (map-indexed vector (:sample-rows csv-analysis))]
                        (println (format "  Row %d: %s" (inc idx) (str/join " | " row))))
                      
                      (save-csv-file (:content download-result) (:filename csv-attachment))
                      
                      (println "\n=== SUMMARY ===")
                      (println (format "Ticket: JESI-7693 (%s)" status))
                      (println (format "CSV: ✓ Downloaded (%d bytes, %d rows)" (:size download-result) (:row-count csv-analysis)))
                      (println (format "Team disambiguation: %s" 
                                       (if (:has-team-disambiguation csv-analysis) "✓ Detected" "✗ Not found"))))
                    (println (format "✗ CSV parse error: %s" (:error csv-analysis)))))
                (println (format "✗ Download failed: %s" (:error download-result))))))
          (do
            (println "✗ users-for-approval.csv not found")
            (println "Available files:")
            (doseq [att attachments]
              (println (format "  - %s" (:filename att)))))))
      
      {:success true :issue issue :status status})
    
    (catch Exception e
      (println (format "✗ Error: %s" (.getMessage e)))
      {:success false :error (.getMessage e)})))

;; Run the function when script is loaded
(fetch-jesi-7693)