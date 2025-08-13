(ns test-json-encoding
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(println "clj-http version test...")
(println "Testing different parameter combinations...")

;; Test with form-params (which should work similarly)
(println "\nTest 1: form-params")
(try
  (let [response (http/post "https://httpbin.org/post"
                            {:form-params {:test "value"
                                         :nested "data"}
                             :as :json})]
    (println "Form data echoed back:" (get-in response [:body :form])))
  (catch Exception e
    (println "Error:" (.getMessage e))))

;; Try with json-params and debug output
(println "\nTest 2: Checking what clj-http is doing with json-params")
(let [request-map {:json-params {:test "value"}
                   :as :json}]
  (println "Request map:" request-map)
  (println "Keys in request:" (keys request-map))
  (println "json-params value:" (:json-params request-map)))

;; Let's see if there's a middleware issue
(println "\nTest 3: Using wrap-json-params middleware explicitly")
(try
  (require '[clj-http.middleware :as middleware])
  (let [wrapped-fn (middleware/wrap-json-params identity)
        transformed (wrapped-fn {:json-params {:test "value"}})]
    (println "Transformed request:" transformed))
  (catch Exception e
    (println "Error loading middleware:" (.getMessage e))))