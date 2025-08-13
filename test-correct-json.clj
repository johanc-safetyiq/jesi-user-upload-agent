(ns test-correct-json
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(println "Testing correct json-params usage...")

;; Test 1: json-params WITHOUT content-type
(println "\nTest 1: json-params WITHOUT :content-type")
(try
  (let [response (http/post "https://httpbin.org/post"
                            {:json-params {:test "value"
                                         :nested {:key "data"}}
                             :as :json})]
    (println "Response status:" (:status response))
    (println "Request data echoed back:" (get-in response [:body :json]))
    (println "Content-Length:" (get-in response [:body :headers :Content-Length])))
  (catch Exception e
    (println "Error:" (.getMessage e))))

;; Test 2: json-params with headers but no content-type option
(println "\nTest 2: json-params with auth headers")
(try
  (let [response (http/post "https://httpbin.org/post"
                            {:json-params {:test "value"
                                         :nested {:key "data"}}
                             :headers {"Authorization" "Bearer test"}
                             :as :json})]
    (println "Response status:" (:status response))
    (println "Request data echoed back:" (get-in response [:body :json]))
    (println "Content-Length:" (get-in response [:body :headers :Content-Length]))
    (println "Content-Type sent:" (get-in response [:body :headers :Content-Type])))
  (catch Exception e
    (println "Error:" (.getMessage e))))