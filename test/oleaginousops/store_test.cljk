(ns oleaginousops.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [oleaginousops.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial plantations"
    (let [plantations {"plantation-001" {:id "plantation-001" :name "Test Plantation Block"}}
          st (store/mem-store {:initial-plantations plantations})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-plantation-retrieval
  (testing "Retrieve existing plantation"
    (let [plantation {:id "plantation-001" :name "Test Plantation Block"}
          st (store/mem-store {:initial-plantations {"plantation-001" plantation}})]
      (is (= plantation (store/registered-plantation st "plantation-001")))))

  (testing "Retrieve non-existent plantation"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-plantation st "no-such-plantation")))))

  (testing "nil plantation-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-plantations {"plantation-001" {:id "plantation-001"}}})]
      (is (nil? (store/registered-plantation st nil))))))

(deftest add-plantation-test
  (testing "Register a new plantation"
    (let [st (store/mem-store)
          plantation-data {:id "plantation-002" :name "New Plantation Block"}
          result (store/add-plantation st "plantation-002" plantation-data)]
      (is (= plantation-data result))
      (is (= plantation-data (store/registered-plantation st "plantation-002")))))

  (testing "Update an existing plantation"
    (let [st (store/mem-store {:initial-plantations {"plantation-001" {:id "plantation-001"}}})
          updated {:id "plantation-001" :name "Renamed Plantation Block"}
          result (store/add-plantation st "plantation-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-plantation st "plantation-001"))))))
