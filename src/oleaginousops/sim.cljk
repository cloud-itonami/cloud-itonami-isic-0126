(ns oleaginousops.sim
  "Simple simulation/demo runner for the Oleaginous-Fruit Plantation
  Operations Coordinator actor. Used to validate that the actor flow
  compiles and basic proposal flow works. Mirrors `berrynutops.sim`
  (cloud-itonami-isic-0125)."
  (:require [oleaginousops.operation :as operation]
            [oleaginousops.store :as store]))

(defn demo
  "Run a simple demo scenario: register a plantation block, propose a
  plantation-record log, and check the disposition flow."
  []
  (let [;; Create store with a registered plantation block
        st (store/mem-store
            {:initial-plantations
             {"plantation-001"
              {:id "plantation-001"
               :name "Test Plantation Block"
               :fruit-class "oil-palm"}}})

        ;; Build actor
        actor (operation/build st)

        ;; Create a request to log a plantation record
        request {:op :log-plantation-record
                 :plantation-id "plantation-001"
                 :record-type "harvest"
                 :count 500
                 :notes "healthy yield"}

        ;; Context with phase 0 (simulation)
        context {:actor-id "oleaginous-ops-01"
                 :role :plantation-operator
                 :phase :phase-0}]

    (println "=== Oleaginous-Fruit Plantation Operations Coordinator Demo ===")
    (println "Demo plantation block: plantation-001")
    (println "Request: log-plantation-record")
    (println "Phase: phase-0 (simulation)")
    (println "Expected: escalate (phase-0 forces human review of all commits)")
    (println)
    (let [result (actor request context)]
      (println "Result disposition:" (:disposition result))
      result)))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
)
