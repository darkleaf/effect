(ns darkleaf.effect.middleware.reduced-test
  (:require
   [darkleaf.generator.core :as gen :refer [generator yield]]
   [darkleaf.effect.core :as e :refer [effect]]
   [darkleaf.effect.middleware.reduced :as reduced]
   [clojure.test :as t]))

(t/deftest maybe-example
  (let [f* (fn [x]
             (generator
               (+ 5 (yield (effect :maybe x)))))
        f* (-> f*
               e/wrap
               reduced/wrap-reduced)]
    (t/testing :just
      (let [gen (f* 1)]
        (t/is (= (effect :maybe 1) (gen/value gen)))
        (gen/next gen 1)
        (t/is (= 6 (gen/value gen)))
        (t/is (gen/done? gen))))
    (t/testing :nothing
      (let [gen (f* nil)]
        (t/is (= (effect :maybe nil) (gen/value gen)))
        (gen/next gen (reduced nil))
        (t/is (= nil (gen/value gen)))
        (t/is (gen/done? gen))))))
