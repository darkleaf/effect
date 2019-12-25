(ns darkleaf.effect.core-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.script :as script]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(t/deftest simple
  (let [ef           (fn [x]
                       (eff
                         (let [rnd (! (effect [:random]))]
                           (- (* 2. x rnd)
                              x))))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:random] 0.75))
            f                 (fn [x]
                                (e/perform effect-!>coeffect continuation [x]))]
        (t/is (= 0.5 (f 1)))))
    (t/testing "script"
      (let [script [{:args [1]}
                    {:effect   [:random]
                     :coeffect 0.75}
                    {:return 0.5}]]
        (script/test continuation script)))))

#?(:cljs
   (defn- ->async-effect-!>coeeffect [effect-!>coeffect]
     (fn [effect respond raise]
       (js/setTimeout (fn []
                        (respond (effect-!>coeffect effect)))
                      0))))

#?(:cljs
   (t/deftest async-use-case
     (t/async done
              (let [ef                (fn [x]
                                        (eff
                                          (let [rnd (! (effect [:random]))]
                                            (- (* 2. x rnd)
                                               x))))
                    effect-!>coeffect (fn [effect]
                                        (match effect
                                               [:random] 0.75))
                    effect-!>coeffect (->async-effect-!>coeeffect effect-!>coeffect)
                    continuation      (e/continuation ef)
                    f                 (fn [x respond raise]
                                        (e/perform effect-!>coeffect
                                                   continuation
                                                   [x]
                                                   respond raise))]
                (f 1
                   (fn [result]
                     (t/is (= 0.5 result))
                     (done))
                   (fn [error]
                     (t/is (nil? error))
                     (done)))))))

(t/deftest stack-use-case
  (let [nested-ef    (fn [x]
                       (eff
                         (! (effect [:prn "start nested-ef"]))
                         (! (effect [:prn x]))
                         (! (effect [:read]))))
        ef           (fn [x]
                       (eff
                         (! (effect [:prn "start ef"]))
                         (! (nested-ef x))))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:prn _]  nil
                                       [:read]   "input string"))
            f                 (fn [x]
                                (e/perform effect-!>coeffect continuation [x]))]
        (t/is (= "input string" (f  "some val")))))
    (t/testing "script"
      (let [script [{:args ["some val"]}
                    {:effect   [:prn "start ef"]
                     :coeffect nil}
                    {:effect   [:prn "start nested-ef"]
                     :coeffect nil}
                    {:effect   [:prn "some val"]
                     :coeffect nil}
                    {:effect   [:read]
                     :coeffect "input string"}
                    {:return "input string"}]]
        (script/test continuation script)))))


(t/deftest types-of-effects
  (let [ef           (fn []
                       (eff
                         [(! (effect [:effect-1 :val-1]))
                          (! (effect {:type :effect-2
                                      :arg  :val-2}))
                          (! (effect 'effect-3))]))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:effect-1 arg] arg
                                       {:type :effect-2, :arg arg} arg
                                       'effect-3 :val-3))
            f                 (fn []
                                (e/perform effect-!>coeffect continuation []))]
        (t/is (= [:val-1 :val-2 :val-3] (f)))))
    (t/testing "script"
      (let [script [{:args []}
                    {:effect   [:effect-1 :val-1]
                     :coeffect :val-1}
                    {:effect   {:type :effect-2
                                :arg  :val-2}
                     :coeffect :val-2}
                    {:effect   'effect-3
                     :coeffect :val-3}
                    {:return [:val-1 :val-2 :val-3]}]]
        (script/test continuation script)))))

(t/deftest fallback
  (let [ef           (fn [x]
                       (eff
                         (let [a (! (effect [:eff]))
                               b (! [:not-effect])
                               c (! (inc x))]
                           [a b c])))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:eff] :coeff))
            f                 (fn [x]
                                (e/perform effect-!>coeffect continuation [x]))]
        (t/is (= [:coeff [:not-effect] 1] (f 0)))))
    (t/testing "script"
      (let [script [{:args [0]}
                    {:effect   [:eff]
                     :coeffect :coeff}
                    {:return [:coeff [:not-effect] 1]}]]
        (script/test continuation script)))))

(t/deftest effect-as-value
  (let [effect-data  [:prn 1]
        test-effect  (effect effect-data)
        ef           (fn []
                       (eff
                         (! test-effect)))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:prn 1]
                       :coeffect nil}
                      {:return nil}]]
    (script/test continuation script)))

(t/deftest reduce-test
  (let [interpretator (fn [ef & args]
                        (let [continuation      (e/continuation ef)
                              effect-!>coeffect (constantly ::not-used-coeffect)]
                          (e/perform effect-!>coeffect continuation args)))
        str*          (fn [& args]
                        (eff
                          (! (effect [:print args]))
                          (apply str args)))
        with-reduced  (fn [acc v]
                        (if (= :done v)
                          (reduced v)
                          v))
        with-reduced* (fn [_ v]
                        (eff
                          (! (effect [:print v]))
                          (if (= :done v)
                            (reduced v)
                            v)))]
    (t/are [coll] (= (reduce str coll)
                     (interpretator e/reduce str* coll)
                     (interpretator e/reduce str coll))
      nil
      []
      [:a]
      [:a :b]
      [:a :b :c])
    (t/are [val coll] (= (reduce str val coll)
                         (interpretator e/reduce str* val coll)
                         (interpretator e/reduce str val coll))

      "acc" []
      "acc" [:a]
      "acc" [:a :b]
      "acc" [:a :b :c])
    (t/are [coll] (= (reduce with-reduced coll)
                     (interpretator e/reduce with-reduced* coll)
                     (interpretator e/reduce with-reduced coll))
      [:done]
      [1 :done]
      [1 2 3 :done 4 5])))

(t/deftest mapv-test
  (let [interpretator (fn [ef & args]
                        (let [continuation      (e/continuation ef)
                              effect-!>coeffect (constantly ::not-used-coeffect)]
                          (e/perform effect-!>coeffect continuation args)))
        str*          (fn [& args]
                        (eff
                          (! (effect [:print args]))
                          (apply str args)))]
    (t/are [colls] (= (apply mapv str colls)
                      (apply interpretator e/mapv str* colls)
                      (apply interpretator e/mapv str colls))
      [nil]
      [[]]
      [[0]]
      [[0 1]]
      [[0 1 2]]
      [#{1 2 3}]
      [{:a 1, :b 2}]

      [nil nil]
      [[] []]
      [[0] [1 2]]
      [[0 1] [2]]
      [#{1 2} [3 4]])))
