(ns test-op-lookup
  (:require [user-upload.auth.onepassword :as op]))

(println "Testing 1Password lookup for qbirt...")
(let [result (op/fetch-credentials-for-tenant "qbirt")]
  (println "Result:" result)
  (when (:success result)
    (println "Successfully found credentials!")
    (println "Email:" (:email result))
    (println "Multiple items found:" (:multiple-items-found result))
    (println "Item used:" (:item-used result))))