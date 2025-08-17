(ns user_upload.log
  "Unified logging infrastructure with JSON output using Timbre.
   All logging should go through this namespace to ensure consistency."
  (:require [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [user_upload.config :as config]
            [clojure.string :as str]))

(defn json-output-fn
  "Custom output function that formats log entries as JSON."
  [data]
  (let [{:keys [level ?err msg_ ?ns-str ?file timestamp_ ?line vargs]} data
        ;; Extract message and context from vargs
        [message context] (if (and vargs (> (count vargs) 1))
                            [(first vargs) (second vargs)]
                            [(force msg_) nil])
        ;; Build base log entry
        base-log (cond-> {:timestamp (force timestamp_)
                          :level (name level)
                          :message message}
                   ?ns-str (assoc :namespace ?ns-str)
                   ?file (assoc :file ?file)
                   ?line (assoc :line ?line)
                   ;; Merge context data if provided and is a map
                   (and context (map? context)) (merge context))
        ;; Add error info if present
        log-with-error (if ?err
                         (assoc base-log :error {:message (.getMessage ?err)
                                                 :type (-> ?err class .getName)
                                                 :stacktrace (when (>= (compare level :error) 0)
                                                              (with-out-str 
                                                                (.printStackTrace ?err)))})
                         base-log)]
    (json/generate-string log-with-error)))

(defn setup-logging!
  "Set up timbre logging with JSON output and appropriate log level."
  []
  (let [cfg (config/config)
        log-level (get cfg :log-level :info)]
    (timbre/merge-config!
     {:level log-level
      :output-fn json-output-fn
      :appenders {:println {:enabled? true
                            :fn (fn [data] (println (json-output-fn data)))}}})))

;; Convenience logging functions that accept structured data
(defn trace
  "Log a trace message with optional structured data context."
  ([message] 
   (timbre/trace message))
  ([message data]
   (timbre/trace message data)))

(defn debug
  "Log a debug message with optional structured data context."
  ([message]
   (timbre/debug message))
  ([message data]
   (timbre/debug message data)))

(defn info
  "Log an info message with optional structured data context."
  ([message] 
   (timbre/info message))
  ([message data]
   (timbre/info message data)))

(defn warn
  "Log a warning message with optional structured data context."
  ([message]
   (timbre/warn message))
  ([message data]
   (timbre/warn message data)))

(defn error
  "Log an error message with optional exception and structured data context."
  ([message]
   (timbre/error message))
  ([message ex]
   (if (map? ex)
     ;; If ex is actually data, not an exception
     (timbre/error message ex)
     ;; If ex is an exception
     (timbre/error ex message)))
  ([message ex data]
   (timbre/error ex message data)))

;; Specialized logging functions for common patterns
(defn operation
  "Log an operation with structured context. 
   Useful for API calls, batch operations, etc."
  [operation-name data]
  (info operation-name data))

(defn progress
  "Log progress updates. Automatically uses debug level to reduce verbosity.
   Example: (progress \"Processing batch\" {:current 5 :total 82})"
  [message data]
  (debug message data))

(defn result
  "Log operation results with success/failure status."
  [operation-name success? data]
  (let [level (if success? :info :error)
        message (str operation-name (if success? " succeeded" " failed"))]
    (case level
      :info (info message data)
      :error (error message data))))

;; Initialize logging when the namespace is loaded
(defonce _init-logging
  (do
    (setup-logging!)
    true))