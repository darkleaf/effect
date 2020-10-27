(ns darkleaf.effect.middleware.contract-test
  (:require
   [darkleaf.generator.core :as gen :refer [generator yield]]
   [darkleaf.effect.core :as e :refer [effect]]
   [darkleaf.effect.middleware.contract :as contract]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(t/deftest success-test
  (let [contract {'my/f*    {:args   (fn [x] (= :ok x))
                             :return (fn [ret] (= :ok ret))}
                  :effect-1 {:effect   (fn [x] (int? x))
                             :coeffect (fn [x] (= x :ok))}
                  :effect-2 {:effect   (fn [x] (string? x))
                             :coeffect (fn [x] (= x :ok))}}
        f*       (fn [x]
                   (generator
                     (yield (effect :effect-1 1))
                     (yield (effect :effect-2 "str"))
                     :ok))
        f*       (-> f*
                     e/wrap
                     (contract/wrap-contract contract 'my/f*))
        gen      (f* :ok)]
    (t/is (= (effect :effect-1 1) (gen/value gen)))
    (gen/next gen :ok)
    (t/is (= (effect :effect-2 "str") (gen/value gen)))
    (gen/next gen :ok)
    (t/is (= :ok (gen/value gen)))
    (t/is (gen/done? gen))))

(t/deftest args-test
  (let [contract {'my/f* {:args   (fn [x] (= :ok x))
                          :return (constantly true)}}
        f*       (fn [x]
                   (generator))
        f*       (-> f*
                     e/wrap
                     (contract/wrap-contract contract 'my/f*))
        ex       (try (f* :wrong) (catch ExceptionInfo ex ex))]
    (t/is (= "The args are rejected by a predicate"
             (ex-message ex)))
    (t/is (= {:args [:wrong] :path ['my/f* :args]}
             (ex-data ex)))))

(t/deftest return-test
  (let [contract {'my/f* {:args   (constantly true)
                          :return (fn [ret] (= :ok ret))}}
        f*       (fn [x]
                   (generator
                     :not-ok))
        f*       (-> f*
                     e/wrap
                     (contract/wrap-contract contract 'my/f*))
        ex       (try (f* :ok) (catch ExceptionInfo ex ex))]

    (t/is (= "The return value is rejected by a predicate"
             (ex-message ex)))
    (t/is (= {:return :not-ok :path   ['my/f* :return]}
             (ex-data ex)))))

(t/deftest effect-test
  (let [contract {'my/f*       {:args   (constantly true)
                                :return (constantly true)}
                  :some-effect {:effect   (fn [x] (int? x))
                                :coeffect (constantly true)}}
        f*       (fn [x]
                   (generator
                     (yield (effect :some-effect :wrong))))
        f*       (-> f*
                     e/wrap
                     (contract/wrap-contract contract 'my/f*))
        ex       (try (f* :ok) (catch ExceptionInfo ex ex))]
    (t/is (= "The effect args are rejected by a predicate"
             (ex-message ex)))
    (t/is (= {:args [:wrong] :path [:some-effect :effect]}
             (ex-data ex)))))

(t/deftest coeffect-test
  (let [contract {'my/f*       {:args   (constantly true)
                                :return (constantly true)}
                  :some-effect {:effect   (constantly true)
                                :coeffect (fn [x] (= x :ok))}}
        f*       (fn [x]
                   (generator
                     (yield (effect :some-effect))
                     :ok))
        f*       (-> f*
                     e/wrap
                     (contract/wrap-contract contract 'my/f*))
        gen      (f* :ok)
        _        (t/is (= (effect :some-effect) (gen/value gen)))
        ex       (try (gen/next gen :wrong) (catch ExceptionInfo ex ex))]
    (t/is (= "The coeffect is rejected by a predicate"
             (ex-message ex)))
    (t/is (= {:coeffect :wrong :path [:some-effect :coeffect]}
             (ex-data ex)))
    #_(t/is (gen/done? gen))))
