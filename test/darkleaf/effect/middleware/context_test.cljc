(ns darkleaf.effect.middleware.context-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.script :as script]
   [darkleaf.effect.middleware.context :as context]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(t/deftest state
  (let [ef (fn [x]
             (eff
               [x
                (! (effect [:update inc]))
                (! (effect [:update + 2]))
                (! (effect [:get]))]))]
    (t/testing "intepretator"
      (let [continuation      (-> ef
                                  (e/continuation)
                                  (context/wrap-context))
            effect-!>coeffect (fn [[context effect]]
                                (match effect
                                       [:get]
                                       [context (:state context)]
                                       [:update f & args]
                                       (let [context (apply update context :state f args)]
                                         [context (:state context)])))
            f                 (fn [x] (e/perform effect-!>coeffect continuation [{:state 0} [x]]))]
        (t/is (= [{:state 3} [0 1 3 3]]
                 (f 0)))))
    (t/testing "script"
      (let [continuation (e/continuation ef)
            script       [{:args [0]}
                          {:effect   [:update inc]
                           :coeffect 1}
                          {:effect   [:update + 2]
                           :coeffect 3}
                          {:effect   [:get]
                           :coeffect 3}
                          {:return [0 1 3 3]}]]
        (script/test continuation script)))
    (t/testing "script with applied middleware"
      (let [continuation (-> ef
                             (e/continuation)
                             (context/wrap-context))
            script       [{:args [{:state 0} [0]]}
                          {:effect   [{:state 0} [:update inc]]
                           :coeffect [{:state 1} 1]}
                          {:effect   [{:state 1} [:update + 2]]
                           :coeffect [{:state 3} 3]}
                          {:effect   [{:state 3} [:get]]
                           :coeffect [{:state 3} 3]}
                          {:return [{:state 3} [0 1 3 3]]}]]
        (script/test continuation script)))))
