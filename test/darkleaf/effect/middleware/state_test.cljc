(ns darkleaf.effect.middleware.state-test
  (:require
   [darkleaf.generator.core :as gen :refer [generator yield]]
   [darkleaf.effect.core :as e :refer [effect]]
   [darkleaf.effect.middleware.state :as state]
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
