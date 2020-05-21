(ns darkleaf.effect.middleware.log-test
  (:require
   [darkleaf.effect.core :as e :refer [with-effects ! effect]]
   [darkleaf.effect.script :as script]
   [darkleaf.effect.middleware.log :as log]
   [clojure.test :as t]))

(t/deftest suspend-resume
  (let [ef           (fn [x]
                       (with-effects
                         (let [a (! (effect :suspend))
                               b (! (effect :effect))
                               c (! (effect :suspend))]
                           [x a b c])))
        handlers     {:effect  (fn [] :coeffect)
                      :suspend (fn [] ::log/suspend)}
        ;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        continuation (-> (e/continuation ef)
                         (log/wrap-log))
        suspended    (e/perform handlers continuation [:arg])
        _            (t/is (= [::log/suspended [{:coeffect    [:arg]
                                                 :next-effect [:suspend]}]]
                              suspended))
        log          (last suspended)
        ;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        continuation (-> (e/continuation ef)
                         (log/wrap-log)
                         (log/resume log))
        suspended    (e/perform handlers continuation :value-1)
        _            (t/is (= [::log/suspended [{:coeffect    [:arg]
                                                 :next-effect [:suspend]}
                                                {:coeffect    :value-1
                                                 :next-effect [:effect]}
                                                {:coeffect    :coeffect
                                                 :next-effect [:suspend]}]]
                              suspended))
        log          (last suspended)
        ;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        continuation (-> (e/continuation ef)
                         (log/wrap-log)
                         (log/resume log))
        result       (e/perform handlers continuation :value-2)]
    (t/is (= [::log/result
              [:arg :value-1 :coeffect :value-2]
              [{:coeffect    [:arg]
                :next-effect [:suspend]}
               {:coeffect    :value-1
                :next-effect [:effect]}
               {:coeffect    :coeffect
                :next-effect [:suspend]}
               {:coeffect :value-2,
                :next-effect
                [:arg :value-1 :coeffect :value-2]}]]
             result))))
