#!/usr/bin/env clj

(require '[user-upload.workflow.orchestrator :as orch])
(require '[user-upload.workflow.approval :as approval])
(require '[clojure.string :as str])

;; Test data simulating perfect CSV match
(def test-attachment
  {:filename "users.csv"
   :content (.getBytes (slurp "test-approval.csv"))})

(def test-ticket-open
  {:fields {:status {:name "Open"}}})

(def test-ticket-review
  {:fields {:status {:name "Review"}}})

;; Test 1: Perfect CSV match should NOT require approval
(println "\n=== Test 1: Perfect CSV match (should proceed directly) ===")
(let [parse-result {:success true
                    :headers ["email" "first name" "last name" "job title" 
                             "mobile number" "teams" "user role"]
                    :data [{:email "john@example.com" :first-name "John"}]}
      validation-result {:success true
                        :valid-data [{:email "john@example.com"}]
                        :mapping-used nil}]
  (println "Approval required?" 
           (approval/workflow-approval-required? validation-result parse-result)))

;; Test 2: Column mapping used should require approval
(println "\n=== Test 2: Column mapping used (should require approval) ===")
(let [parse-result {:success true
                    :headers ["Email Address" "FirstName" "Surname" "Role"]
                    :data [{:email "john@example.com" :first-name "John"}]}
      validation-result {:success true
                        :valid-data [{:email "john@example.com"}]
                        :mapping-used {"Email Address" "email"
                                     "FirstName" "first name"
                                     "Surname" "last name"
                                     "Role" "user role"}}]
  (println "Approval required?" 
           (approval/workflow-approval-required? validation-result parse-result)))

;; Test 3: AI sheet detection used should require approval
(println "\n=== Test 3: AI sheet detection (should require approval) ===")
(let [parse-result {:success true
                    :headers ["email" "first name" "last name"]
                    :detected-sheet "User"
                    :data [{:email "john@example.com"}]}
      validation-result {:success true
                        :valid-data [{:email "john@example.com"}]}]
  (println "Approval required?" 
           (approval/workflow-approval-required? validation-result parse-result)))

(println "\n=== All tests completed ===")
(System/exit 0)