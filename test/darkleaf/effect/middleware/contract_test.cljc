(ns darkleaf.effect.middleware.contract-test
  (:require
   [darkleaf.effect.middleware.contract :as contract]
   [darkleaf.effect.core :as e :refer [! effect with-effects]]
   [darkleaf.effect.script :as script]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(t/deftest success-test
  (let [contract     {'my/effn  {:args   (fn [x] (= :ok x))
                                 :return (fn [ret] (= :ok ret))}
                      :effect-1 {:effect   (fn [x] (int? x))
                                 :coeffect (fn [x] (= x :ok))}
                      :effect-2 {:effect   (fn [x] (string? x))
                                 :coeffect (fn [x] (= x :ok))}}
        effn         (fn [x]
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

(t/deftest args-test
  (let [contract     {'my/effn {:args   (fn [x] (= :ok x))
                                :return (constantly true)}}
        effn         (fn [x]
                       (with-effects))
        continuation (-> effn
                         (e/continuation)
                         (contract/wrap-contract contract 'my/effn))
        script       [{:args [:wrong]}
                      {:thrown {:type    ExceptionInfo
                                :message "The args are rejected by a predicate"
                                :data    {:args [:wrong]
                                          :path ['my/effn :args]}}}]]
    (script/test continuation script)))

(t/deftest return-test
  (let [contract     {'my/effn {:args   (constantly true)
                                :return (fn [ret] (= :ok ret))}}
        effn         (fn [x]
                       (with-effects
                         :not-ok))
        continuation (-> effn
                         (e/continuation)
                         (contract/wrap-contract contract 'my/effn))
        script       [{:args [:ok]}
                      {:thrown {:type    ExceptionInfo
                                :message "The return value is rejected by a predicate"
                                :data    {:return :not-ok
                                          :path   ['my/effn :return]}}}]]
    (script/test continuation script)))

(t/deftest effect-test
  (let [contract     {'my/effn     {:args   (constantly true)
                                    :return (constantly true)}
                      :some-effect {:effect   (fn [x] (int? x))
                                    :coeffect (constantly true)}}
        effn         (fn [x]
                       (with-effects
                         (! (effect :some-effect :wrong))))
        continuation (-> effn
                         (e/continuation)
                         (contract/wrap-contract contract 'my/effn))
        script       [{:args [:ok]}
                      {:thrown {:type    ExceptionInfo
                                :message "The effect args are rejected by a predicate"
                                :data    {:args [:wrong]
                                          :path [:some-effect :effect]}}}]]
    (script/test continuation script)))

(t/deftest coeffect-test
  (let [contract     {'my/effn     {:args   (constantly true)
                                    :return (constantly true)}
                      :some-effect {:effect   (constantly true)
                                    :coeffect (fn [x] (= x :ok))}}
        effn         (fn [x]
                       (with-effects
                         (! (effect :some-effect))
                         :ok))
        continuation (-> effn
                         (e/continuation)
                         (contract/wrap-contract contract 'my/effn))
        script       [{:args [:ok]}
                      {:effect   [:some-effect]
                       :coeffect :wrong}
                      {:thrown {:type    ExceptionInfo
                                :message "The coeffect is rejected by a predicate"
                                :data    {:coeffect :wrong
                                          :path     [:some-effect :coeffect]}}}]]
    (script/test continuation script)))
