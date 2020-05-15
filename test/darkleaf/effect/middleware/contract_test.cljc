(ns darkleaf.effect.middleware.contract-test
  (:require
   [darkleaf.effect.middleware.contract :as contract]
   [darkleaf.effect.core :as e :refer [! effect with-effects]]
   [darkleaf.effect.script :as script]
   [clojure.test :as t]))

(t/deftest usage
  (let [contract {:args     (fn [[x]] (= :ok x))
                  :return   (fn [ret] (= :ok ret))
                  ;;:tag    first
                  :effect-1 {:effect   (fn [[_ x]] (int? x))
                             :coeffect (fn [x] (= x :ok))}
                  :effect-2 {:effect   (fn [[_ x]] (string? x))
                             :coeffect (fn [x] (= x :ok))}}]
    (t/testing "success"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect [:effect-1 1]))
                             (! (effect [:effect-2 "str"]))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract))
            script       [{:args [:ok]}
                          {:effect   [:effect-1 1]
                           :coeffect :ok}
                          {:effect   [:effect-2 "str"]
                           :coeffect :ok}
                          {:return :ok}]]
        (script/test continuation script)))
    (t/testing "args"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect [:effect-1 1]))
                             (! (effect [:effect-2 "str"]))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract))
            script       [{:args [:wrong]}
                          {:thrown (ex-info "The value is mismatched by a predicate"
                                            {:actual [:wrong]
                                             :path   [:args]})}]]
        (script/test continuation script)))
    (t/testing "return"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect [:effect-1 1]))
                             (! (effect [:effect-2 "str"]))
                             :not-ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract))
            script       [{:args [:ok]}
                          {:effect   [:effect-1 1]
                           :coeffect :ok}
                          {:effect   [:effect-2 "str"]
                           :coeffect :ok}
                          {:thrown (ex-info "The value is mismatched by a predicate"
                                            {:actual :not-ok
                                             :path   [:return]})}]]
        (script/test continuation script)))
    (t/testing "effect"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect [:effect-1 :wrong]))
                             (! (effect [:effect-2 "str"]))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract))
            script       [{:args [:ok]}
                          {:thrown (ex-info "The value is mismatched by a predicate"
                                            {:actual [:effect-1 :wrong]
                                             :path   [:effect-1 :effect]})}]]
        (script/test continuation script)))
    (t/testing "coeffect"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect [:effect-1 1]))
                             (! (effect [:effect-2 "str"]))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract))
            script       [{:args [:ok]}
                          {:effect   [:effect-1 1]
                           :coeffect :wrong}
                          {:thrown (ex-info "The value is mismatched by a predicate"
                                            {:actual :wrong
                                             :path   [:effect-1 :coeffect]})}]]
        (script/test continuation script)))))
