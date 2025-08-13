(ns test-json-direct
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(println "Testing json-params with different clj-http versions...")

;; Check what middleware is being applied by default
(println "\nDefault middleware stack:")
(println (take 5 http/default-middleware))

;; Try the request with json-params using request function directly
(println "\nUsing http/request directly:")
(try
  (let [response (http/request {:method :post
                                :url "https://httpbin.org/post"
                                :json-params {:test "value"}
                                :as :json})]
    (println "Status:" (:status response))
    (println "JSON echoed:" (get-in response [:body :json])))
  (catch Exception e
    (println "Error:" (.getMessage e))))

;; Use the json middleware explicitly
(println "\nManually applying JSON encoding:")
(try
  (let [data {:test "value" :nested {:key "data"}}
        response (http/post "https://httpbin.org/post"
                           {:body (json/generate-string data)
                            :content-type :json
                            :accept :json
                            :as :json})]
    (println "Status:" (:status response))
    (println "JSON echoed:" (get-in response [:body :json])))
  (catch Exception e
    (println "Error:" (.getMessage e))))