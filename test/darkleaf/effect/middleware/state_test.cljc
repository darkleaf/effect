(ns darkleaf.effect.middleware.state-test
  (:require
   [darkleaf.generator.core :as gen :refer [generator yield]]
   [darkleaf.effect.core :as e :refer [effect]]
   [darkleaf.effect.middleware.state :as state]
   [darkleaf.effect.middleware.contract :as contract]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(t/deftest state
  (let [f*  (fn []
              (generator
                [(yield (effect ::state/get))
                 (yield (effect ::state/gets str " - state"))
                 (yield (effect ::state/put 0))
                 (yield (effect ::state/modify + 1))]))
        f*  (-> f*
                e/wrap
                state/wrap-state)
        gen (f* 42)]
    (t/is (= [1 [42 "42 - state" 0 1]] (gen/value gen)))
    (t/is (gen/done? gen))))

(t/deftest state+contract
  (let [f*       (fn []
                   (generator
                     [(yield (effect ::state/get))
                      (yield (effect ::state/gets str " - state"))
                      (yield (effect ::state/put 0))
                      (yield (effect ::state/modify + 1))]))
        contract (merge state/contract
                        {`f* {:args   (fn [] true)
                              :return vector?}})
        f*       (-> f*
                     e/wrap
                     (contract/wrap-contract contract `f*)
                     state/wrap-state)
        gen      (f* 42)]
    (t/is (= [1 [42 "42 - state" 0 1]] (gen/value gen)))
    (t/is (gen/done? gen))))
