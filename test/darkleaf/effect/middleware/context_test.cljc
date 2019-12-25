(ns darkleaf.effect.middleware.context-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.script :as script]
   [darkleaf.effect.middleware.context :as context]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(t/deftest state
  (let [ef                (fn []
                            (eff
                             [(! (effect [:update inc]))
                              (! (effect [:update + 2]))
                              (! (effect [:get]))]))
        effect-!>coeffect (fn [[context effect]]
                            (match effect
                                   [:get]
                                   [context (:state context)]

                                   [:update f & args]
                                   (let [context (apply update context :state f args)]
                                     [context (:state context)])))
        continuation (-> (e/continuation ef)
                         (context/wrap-context))]
    (t/is (= [{:state 3} [1 3 3]]
             (e/perform effect-!>coeffect continuation [{:state 0} []])))))
