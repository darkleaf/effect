(ns darkleaf.effect.middleware.reduced-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.script :as script]
   [darkleaf.effect.middleware.reduced :as reduced]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(t/deftest maybe-example
  (let [ef                (fn [x]
                            (eff
                              (+ 5 (! (effect [:maybe x])))))
        effect-!>coeffect (fn [effect]
                            (match effect
                                   [:maybe nil] (reduced nil)
                                   [:maybe val] val))]
    (t/testing "interpretator"
      (let [continuation (-> (e/continuation ef)
                             (reduced/wrap-reduced))]
        (t/is (= 6 (e/perform effect-!>coeffect continuation [1])))
        (t/is (= nil (e/perform effect-!>coeffect continuation [nil])))))
    (t/testing "script"
      (t/testing :just
        (let [continuation (e/continuation ef)
              script       [{:args [1]}
                            {:effect   [:maybe 1]
                             :coeffect 1}
                            {:return 6}]]
          (script/test continuation script)))
      (t/testing :nothing
        (let [continuation (e/continuation ef)
              script       [{:args [nil]}
                            {:final-effect [:maybe nil]}]]
          (script/test continuation script))))))
