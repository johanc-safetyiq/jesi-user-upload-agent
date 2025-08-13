(ns test-actual-request
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(println "Testing actual HTTP request with json-params...")

;; Start a simple server to receive the request
(defn test-request []
  (try
    ;; Make a request to httpbin.org which echoes back what we send
    (let [response (http/post "https://httpbin.org/post"
                              {:json-params {:test "value"
                                           :nested {:key "data"}}
                               :content-type :json
                               :as :json})]
      (println "\nResponse status:" (:status response))
      (println "Request data echoed back:" (get-in response [:body :json]))
      (println "Headers sent:" (get-in response [:body :headers])))
    (catch Exception e
      (println "Error:" (.getMessage e)))))

(test-request)

;; Now test with explicit body
(println "\n\nTesting with :body and manual JSON encoding...")
(defn test-body-request []
  (try
    (let [response (http/post "https://httpbin.org/post"
                              {:body (json/generate-string {:test "value"
                                                           :nested {:key "data"}})
                               :headers {"Content-Type" "application/json"}
                               :as :json})]
      (println "\nResponse status:" (:status response))
      (println "Request data echoed back:" (get-in response [:body :json]))
      (println "Headers sent:" (get-in response [:body :headers])))
    (catch Exception e
      (println "Error:" (.getMessage e)))))

(test-body-request)