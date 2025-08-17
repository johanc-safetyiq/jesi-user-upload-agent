(ns fetch-ticket-details
  "Utility to fetch ticket details and download attachments"
  (:require
    [user-upload.jira.client :as jira]
    [clj-http.client :as http]
    [user-upload.config :as config]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]))

(defn- auth-headers
  "Generate authentication headers for Jira API requests."
  []
  (let [cfg (config/config)
        email (get-in cfg [:jira :email])
        api-token (get-in cfg [:jira :api-token])
        encoder (java.util.Base64/getEncoder)
        credentials (str email ":" api-token)
        encoded (.encodeToString encoder (.getBytes credentials "UTF-8"))]
    {"Authorization" (str "Basic " encoded)
     "Accept" "application/json"}))

(defn download-attachment
  "Download an attachment from Jira"
  [attachment-url filename]
  (let [headers (auth-headers)
        response (http/get attachment-url
                          {:headers headers
                           :as :byte-array
                           :throw-exceptions false})]
    (if (< (:status response) 400)
      (do
        (with-open [out (io/output-stream filename)]
          (.write out (:body response)))
        (println (format "✓ Downloaded attachment to: %s" filename))
        filename)
      (do
        (println (format "✗ Failed to download attachment: %s" (:status response)))
        nil))))

(defn fetch-ticket-with-csv
  "Fetch ticket details and download CSV attachment if present"
  [issue-key]
  (println (format "\n=== Fetching ticket %s ===" issue-key))
  
  ;; Fetch the issue with attachments
  (let [issue (jira/get-issue-with-attachments issue-key)]
    
    ;; Display basic ticket info
    (println (format "\nTicket: %s" (:key issue)))
    (println (format "Summary: %s" (get-in issue [:fields :summary])))
    (println (format "Status: %s" (get-in issue [:fields :status :name])))
    
    ;; Display comments
    (let [comments (get-in issue [:fields :comment :comments])]
      (println (format "\n%d Comment(s):" (count comments)))
      (doseq [comment (take-last 3 comments)] ; Show last 3 comments
        (println "---")
        (println (format "Author: %s" (get-in comment [:author :displayName])))
        (println (format "Created: %s" (:created comment)))
        (println "Body:")
        (println (:body comment))
        (println)))
    
    ;; Check for CSV attachments
    (let [attachments (get-in issue [:fields :attachment])
          csv-attachments (filter #(str/ends-with? (str/lower-case (:filename %)) ".csv") attachments)]
      
      (println (format "\nTotal attachments: %d" (count attachments)))
      (println (format "CSV attachments: %d" (count csv-attachments)))
      
      (when (seq csv-attachments)
        (println "\nCSV Attachments found:")
        (doseq [att csv-attachments]
          (println (format "  - %s" (:filename att)))
          (println (format "    Size: %s bytes" (:size att)))
          (println (format "    Created: %s" (:created att)))
          (println (format "    Author: %s" (get-in att [:author :displayName])))
          
          ;; Download the CSV
          (let [download-filename (str "downloaded_" (:filename att))]
            (when (download-attachment (:content att) download-filename)
              (println "\nCSV Content preview:")
              (let [content (slurp download-filename)
                    lines (str/split-lines content)]
                (doseq [line (take 10 lines)]
                  (println line))
                (when (> (count lines) 10)
                  (println (format "... and %d more lines" (- (count lines) 10))))))))))))

(defn -main
  [& args]
  (let [issue-key (or (first args) "JESI-7693")]
    (fetch-ticket-with-csv issue-key)))

;; Run with specific ticket
(comment
  (fetch-ticket-with-csv "JESI-7693")
  )