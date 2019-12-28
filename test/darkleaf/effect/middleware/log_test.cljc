(ns darkleaf.effect.middleware.log-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.script :as script]
   [darkleaf.effect.middleware.log :as log]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(t/deftest suspend-resume
  (let [ef                (fn [x]
                            (eff
                              (let [a (! (effect [:suspend]))
                                    b (! (effect [:effect]))
                                    c (! (effect [:suspend]))]
                                [x a b c])))
        effect-!>coeffect (fn [effect]
                            (match effect
                                   [:effect]  :coeffect
                                   [:suspend] ::log/suspend))
        ;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        continuation      (-> (e/continuation ef)
                              (log/wrap-log))
        suspended         (e/perform effect-!>coeffect continuation [:arg])
        _                 (t/is (= [::log/suspended [{:coeffect    [:arg]
                                                      :next-effect [:suspend]}]]
                                   suspended))
        log               (last suspended)
        ;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        continuation      (-> (e/continuation ef)
                              (log/wrap-log)
                              (log/resume log))
        suspended         (e/perform effect-!>coeffect continuation :value-1)
        _                 (t/is (= [::log/suspended [{:coeffect    [:arg]
                                                      :next-effect [:suspend]}
                                                     {:coeffect    :value-1
                                                      :next-effect [:effect]}
                                                     {:coeffect    :coeffect
                                                      :next-effect [:suspend]}]]
                                   suspended))
        log               (last suspended)
        ;; ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        continuation      (-> (e/continuation ef)
                              (log/wrap-log)
                              (log/resume log))
        result            (e/perform effect-!>coeffect continuation :value-2)]
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
