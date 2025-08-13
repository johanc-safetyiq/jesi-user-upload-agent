(ns test-json-params
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(println "Testing clj-http json-params...")

;; Test 1: Using :json-params
(println "\nTest 1: Using :json-params")
(let [options {:json-params {:key "value"}
               :content-type :json
               :headers {"Authorization" "Bearer test"}}]
  (println "Options:" options)
  (println "Has :json-params?" (boolean (:json-params options)))
  (println "Value of :json-params:" (:json-params options)))

;; Test 2: Merging options
(println "\nTest 2: Merging options")
(let [base {:headers {"Authorization" "Bearer test"}
            :as :json}
      with-json {:json-params {:key "value"}}
      merged (merge base with-json {:content-type :json})]
  (println "Base:" base)
  (println "With JSON:" with-json)
  (println "Merged:" merged)
  (println "Has :json-params in merged?" (boolean (:json-params merged)))
  (println "Value of :json-params in merged:" (:json-params merged)))

;; Test 3: The actual merging pattern from the code
(println "\nTest 3: Actual merging pattern")
(let [options {:json-params {:body {:version 1 :type "doc"}}}
      base-headers {"Authorization" "Bearer test"}
      request-options (merge
                        {:headers base-headers
                         :throw-exceptions false
                         :as :json
                         :coerce :always}
                        options
                        (when (:json-params options)
                          {:content-type :json}))]
  (println "Original options:" options)
  (println "Final request-options:" request-options)
  (println "Has :json-params?" (boolean (:json-params request-options)))
  (println "Value of :json-params:" (:json-params request-options)))