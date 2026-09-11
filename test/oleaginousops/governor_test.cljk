(ns oleaginousops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [oleaginousops.governor :as gov]
            [oleaginousops.store :as store]))

(deftest hard-violations-no-plantation-id
  (testing "Hard violation: missing plantation-id"
    (let [req {}
          prop {:op :log-plantation-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (seq (:violations verdict)))
      (is (some #(= :plantation-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-unregistered-plantation
  (testing "Hard violation: plantation-id present but not registered"
    (let [req {:plantation-id "plantation-001"}
          prop {:op :log-plantation-record :effect :propose}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :plantation-not-registered (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-not-propose
  (testing "Hard violation: effect is not :propose"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :log-plantation-record :effect :execute}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-execution (:rule %)) (:violations verdict))))))

(deftest hard-violations-field-equipment-blocked
  (testing "Hard violation: direct field-equipment operation is permanently blocked"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :operate-field-equipment :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :field-equipment-or-spray-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-spray-application-blocked
  (testing "Hard violation: finalizing a spray-application decision is permanently blocked"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :finalize-spray-application :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :field-equipment-or-spray-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-op-not-allowed
  (testing "Hard violation: op outside the closed allowlist"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :dispatch-robot-arm :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

(deftest hard-violations-plantation-count-invalid
  (testing "Hard violation: non-positive logged plantation-record quantity"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :log-plantation-record :effect :propose :count 0 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :plantation-count-invalid (:rule %)) (:violations verdict))))))

(deftest ok-plantation-record-logging
  (testing "OK: valid plantation-record logging with a registered plantation block"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :log-plantation-record :effect :propose :count 500 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:hard? verdict)))
      (is (not (:escalate? verdict))))))

(deftest escalation-bud-rot-concern
  (testing "Escalation: bud rot (Ganoderma) concern ALWAYS escalates, even at high confidence"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :flag-crop-health-concern :effect :propose
                :concern "芽腐病（バッドロット、Ganoderma boninense）の疑い" :confidence 0.95}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict)))))

(deftest escalation-drought-stress-concern
  (testing "Escalation: drought-stress concern ALWAYS escalates, even at high confidence"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :flag-crop-health-concern :effect :propose
                :concern "干ばつストレス（drought stress）の疑い" :confidence 0.95}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict)))))

(deftest escalation-low-confidence
  (testing "Escalation: confidence below the floor"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :log-plantation-record :effect :propose :count 500 :confidence 0.5}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-high-cost
  (testing "Escalation: supply order over the (default) cost threshold"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :order-supplies :effect :propose :cost 1000 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (not (:hard? verdict)))
      (is (:escalate? verdict)))))

(deftest escalation-supply-order-category-specific-threshold
  (testing "Escalation: supply order over its category-specific threshold (equipment: 1000)"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :order-supplies :effect :propose :cost 1200 :confidence 0.9
                :value {:category "equipment"}}
          verdict (gov/check req nil prop s)]
      (is (:escalate? verdict))))

  (testing "OK: equipment order under its higher category threshold"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :order-supplies :effect :propose :cost 800 :confidence 0.9
                :value {:category "equipment"}}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-supply-order-low-cost
  (testing "OK: supply order under the cost threshold"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :order-supplies :effect :propose :cost 100 :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))

(deftest ok-schedule-field-operation
  (testing "OK: scheduling a field operation is a routine coordination op"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          s (store/mem-store {:initial-plantations {"plantation-001" plantation}})
          req {:plantation-id "plantation-001"}
          prop {:op :schedule-field-operation :effect :propose :confidence 0.85}
          verdict (gov/check req nil prop s)]
      (is (:ok? verdict))
      (is (not (:escalate? verdict))))))
