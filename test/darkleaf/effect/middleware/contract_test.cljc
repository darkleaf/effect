(ns darkleaf.effect.middleware.contract-test
  (:require
   [darkleaf.effect.middleware.contract :as contract]
   [darkleaf.effect.core :as e :refer [! effect with-effects]]
   [darkleaf.effect.script :as script]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(t/deftest usage
  (let [contract {'my/effn  {:args   (fn [x] (= :ok x))
                             :return (fn [ret] (= :ok ret))}
                  :effect-1 {:effect   (fn [x] (int? x))
                             :coeffect (fn [x] (= x :ok))}
                  :effect-2 {:effect   (fn [x] (string? x))
                             :coeffect (fn [x] (= x :ok))}}]
    (t/testing "success"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect :effect-1 1))
                             (! (effect :effect-2 "str"))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract 'my/effn))
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
                             (! (effect :effect-1 1))
                             (! (effect :effect-2 "str"))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract 'my/effn))
            script       [{:args [:wrong]}
                          {:thrown {:type    ExceptionInfo
                                    :message "The args are rejected by a predicate"
                                    :data    {:args [:wrong]
                                              :path ['my/effn :args]}}}]]
        (script/test continuation script)))
    (t/testing "return"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect :effect-1 1))
                             (! (effect :effect-2 "str"))
                             :not-ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract 'my/effn))
            script       [{:args [:ok]}
                          {:effect   [:effect-1 1]
                           :coeffect :ok}
                          {:effect   [:effect-2 "str"]
                           :coeffect :ok}
                          {:thrown {:type    ExceptionInfo
                                    :message "The return value is rejected by a predicate"
                                    :data    {:return :not-ok
                                              :path   ['my/effn :return]}}}]]
        (script/test continuation script)))
    (t/testing "effect"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect :effect-1 :wrong))
                             (! (effect :effect-2 "str"))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract 'my/effn))
            script       [{:args [:ok]}
                          {:thrown {:type    ExceptionInfo
                                    :message "The effect args are rejected by a predicate"
                                    :data    {:args [:wrong]
                                              :path [:effect-1 :effect]}}}]]
        (script/test continuation script)))
    (t/testing "coeffect"
      (let [effn         (fn [x]
                           (with-effects
                             (! (effect :effect-1 1))
                             (! (effect :effect-2 "str"))
                             :ok))
            continuation (-> effn
                             (e/continuation)
                             (contract/wrap-contract contract 'my/effn))
            script       [{:args [:ok]}
                          {:effect   [:effect-1 1]
                           :coeffect :wrong}
                          {:thrown {:type    ExceptionInfo
                                    :message "The coeffect is rejected by a predicate"
                                    :data    {:coeffect :wrong
                                              :path     [:effect-1 :coeffect]}}}]]
        (script/test continuation script)))))
