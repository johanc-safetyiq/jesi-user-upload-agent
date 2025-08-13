(ns user-upload.log
  "Logging infrastructure with JSON output using Timbre."
  (:require [taoensso.timbre :as timbre]
            [cheshire.core :as json]
            [user-upload.config :as config]))

(defn json-output-fn
  "Custom output function that formats log entries as JSON."
  [data]
  (let [{:keys [level ?err msg_ ?ns-str ?file timestamp_ ?line]} data
        base-log {:timestamp (force timestamp_)
                  :level (name level)
                  :message (force msg_)
                  :namespace ?ns-str
                  :file ?file
                  :line ?line}
        log-with-error (if ?err
                         (assoc base-log :error {:message (.getMessage ?err)
                                                 :type (-> ?err class .getName)})
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

;; Convenience logging functions that ensure setup has been called
(defn info
  "Log an info message with optional data context."
  ([message] 
   (setup-logging!)
   (timbre/info message))
  ([message data]
   (setup-logging!)
   (timbre/info message data)))

(defn warn
  "Log a warning message with optional data context."
  ([message]
   (setup-logging!)
   (timbre/warn message))
  ([message data]
   (setup-logging!)
   (timbre/warn message data)))

(defn error
  "Log an error message with optional exception and data context."
  ([message]
   (setup-logging!)
   (timbre/error message))
  ([message ex]
   (setup-logging!)
   (timbre/error ex message))
  ([message ex data]
   (setup-logging!)
   (timbre/error ex message data)))

(defn debug
  "Log a debug message with optional data context."
  ([message]
   (setup-logging!)
   (timbre/debug message))
  ([message data]
   (setup-logging!)
   (timbre/debug message data)))

(defn trace
  "Log a trace message with optional data context."
  ([message]
   (setup-logging!)
   (timbre/trace message))
  ([message data]
   (setup-logging!)
   (timbre/trace message data)))

;; Initialize logging when the namespace is loaded
(defonce _init-logging
  (do
    (setup-logging!)
    true))