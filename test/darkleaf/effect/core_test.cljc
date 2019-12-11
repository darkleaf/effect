(ns darkleaf.effect.core-test
  (:require
   [darkleaf.effect.core :as e :refer [eff ! effect]]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(t/deftest simple-use-case
  (let [ef                (fn [x]
                            (eff
                              (let [rnd (! (effect :random))]
                                (- (* 2. x rnd)
                                   x))))
        effect-!>coeffect (fn [effect]
                            (match effect
                                   [:random] 0.75))
        continuation      (e/continuation ef)
        args              [1]]
    (t/is (= 0.5 (e/perform effect-!>coeffect continuation args)))))

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
                                          (let [rnd (! (effect :random))]
                                            (- (* 2. x rnd)
                                               x))))
                    effect-!>coeffect (fn [effect]
                                        (match effect
                                               [:random] 0.75))
                    effect-!>coeffect (->async-effect-!>coeeffect effect-!>coeffect)
                    continuation      (e/continuation ef)
                    args              [1]]
                (e/perform effect-!>coeffect continuation args
                           (fn [result]
                             (t/is (= 0.5 result))
                             (done))
                           (fn [error]
                             (t/is false)
                             (done)))))))

(t/deftest stack-use-case
  (let [nested-ef         (fn [x]
                            (eff
                              (! (effect :prn x))
                              (! (effect :read))))
        ef                (fn [x]
                            (eff
                              (! (effect :prn :ef))
                              (! (nested-ef x))))
        effect-!>coeffect (fn [effect]
                            (match effect
                                   [:prn _]  nil
                                   [:read]   "input string"))
        continuation      (e/continuation ef)]
    (t/is (= "input string" (e/perform effect-!>coeffect continuation ["some val"])))))

(t/deftest script
  (let [ef           (fn [x]
                       (eff
                         (! (effect :some-eff x))))
        continuation (e/continuation ef)]
    (t/testing "correct"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:return :other-value}]]
        (e/test continuation script)))
    (t/testing "final-effect"
      (let [script [{:args [:value]}
                    {:final-effect [:some-eff :value]}]]
        (e/test continuation script)))
    (t/testing "wrong effect"
      (let [script [{:args [:value]}
                    {:effect   [:wrong]
                     :coeffect :other-value}
                    {:return :other-value}]]
        (t/is (= {:type     :fail
                  :expected [:wrong]
                  :actual   [:some-eff :value]
                  :message  "Wrong effect"}
                 (e/test* continuation script)))))
    (t/testing "wrong return"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:return :wrong}]]
        (t/is (= {:type     :fail
                  :expected :wrong
                  :actual   :other-value
                  :message  "Wrong return"}
                 (e/test* continuation script)))))
    (t/testing "wrong final-effect"
      (let [script [{:args [:value]}
                    {:final-effect [:wrong]}]]
        (t/is (= {:type     :fail,
                  :expected [:wrong],
                  :actual   [:some-eff :value],
                  :message  "Wrong final effect"}
                 (e/test* continuation script)))))
    (t/testing "extra effect"
      (let [script [{:args [:value]}
                    {:return :wrong}]]
        (t/is (=  {:type     :fail
                   :expected nil
                   :actual   [:some-eff :value]
                   :message  "Extra effect"}
                  (e/test* continuation script)))))
    (t/testing "missed effect"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:effect   [:extra-eff :value]
                     :coeffect :some-value}
                    {:return :some-other-value}]]
        (t/is (= {:type     :fail
                  :expected [:extra-eff :value]
                  :actual   nil
                  :message  "Misssed effect"}
                 (e/test* continuation script)))))))

(t/deftest trivial-script
  (let [ef           (fn [x]
                       (eff
                         x))
        continuation (e/continuation ef)
        script       [{:args [:value]}
                      {:return :value}]]
      (e/test continuation script)))

(t/deftest fallback-script
  (let [ef           (fn [x]
                       (eff
                         (let [a (! (effect :eff))
                               b (! (inc x))
                               c (! [:key 1 2])]
                           [a b c])))
        continuation (e/continuation ef)
        script       [{:args [0]}
                      {:effect   [:eff]
                       :coeffect :coeff}
                      {:return [:coeff 1 [:key 1 2]]}]]
    (e/test continuation script)))

(t/deftest stack-script
  (let [ef1          (fn []
                       (eff
                         (doseq [i (range 2)]
                           (! (effect :prn i)))))
        ef2          (fn [x]
                       (eff
                         (! (effect :prn x))
                         (! (ef1))
                         :ok))
        ef           (fn []
                       (eff
                         (! (ef2 "foo"))))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:prn "foo"]
                       :coeffect nil}
                      {:effect   [:prn 0]
                       :coeffect nil}
                      {:effect   [:prn 1]
                       :coeffect nil}
                      {:return :ok}]]
    (e/test continuation script)))

(t/deftest effect-as-value-script
  (let [test-effect  (effect :prn 1)
        ef           (fn []
                       (eff
                         (! test-effect)))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   test-effect
                       :coeffect nil}
                      {:return nil}]]
    (e/test continuation script)))

(t/deftest maybe-example
  (let [ef                (fn [x]
                            (eff
                              (+ 5 (! (effect :maybe x)))))
        effect-!>coeffect (fn [effect]
                            (match effect
                                   [:maybe nil] (reduced nil)
                                   [:maybe val] val))]
    (t/testing "interpretator"
      (let [continuation (-> (e/continuation ef)
                             (e/wrap-reduced))]
        (t/is (= 6 (e/perform effect-!>coeffect continuation [1])))
        (t/is (= nil (e/perform effect-!>coeffect continuation [nil])))))
    (t/testing "script"
      (t/testing :just
        (let [continuation (e/continuation ef)
              script       [{:args [1]}
                            {:effect   [:maybe 1]
                             :coeffect 1}
                            {:return 6}]]
          (e/test continuation script)))
      (t/testing :nothing
        (let [continuation (e/continuation ef)
              script       [{:args [nil]}
                            {:final-effect [:maybe nil]}]]
          (e/test continuation script))))))

(t/deftest state-example
  (let [ef                (fn []
                            (eff
                              [(! (effect :update inc))
                               (! (effect :update + 2))
                               (! (effect :get))]))
        effect-!>coeffect (fn [[context effect]]
                            (match effect
                                   [:get]
                                   [context (:state context)]

                                   [:update f & args]
                                   (let [context (apply update context :state f args)]
                                     [context (:state context)])))
        continuation (-> (e/continuation ef)
                         (e/wrap-context))]
    (t/is (= [{:state 3} [1 3 3]]
             (e/perform effect-!>coeffect continuation [{:state 0} []])))))

(t/deftest suspend-resume
  (let [ef                (fn [x]
                            (eff
                              (let [a (! (effect :suspend))
                                    b (! (effect :effect))
                                    c (! (effect :suspend))]
                                [x a b c])))
        effect-!>coeffect (fn [effect]
                            (match effect
                                   [:effect]  :effect-coeffect
                                   [:suspend] ::e/suspend))
        continuation      (-> (e/continuation ef)
                              (e/wrap-suspend))
        suspended         (e/perform effect-!>coeffect continuation [:arg])
        _                 (t/is (= [::e/suspended [{:coeffect    [:arg]
                                                    :next-effect [:suspend]}]]
                                   suspended))
        log               (last suspended)
        continuation      (-> (e/continuation ef)
                              (e/wrap-suspend)
                              (e/resume log))
        suspended         (e/perform effect-!>coeffect continuation :value-1)
        _                 (t/is (= [::e/suspended [{:coeffect    [:arg]
                                                    :next-effect [:suspend]}
                                                   {:coeffect    :value-1
                                                    :next-effect [:effect]}
                                                   {:coeffect    :effect-coeffect
                                                    :next-effect [:suspend]}]]
                                   suspended))
        log               (last suspended)
        continuation      (-> (e/continuation ef)
                              (e/wrap-suspend)
                              (e/resume log))
        done              (e/perform effect-!>coeffect continuation :value-2)]
    (t/is (= [::e/done [:arg :value-1 :effect-coeffect :value-2]]
             done))))

(t/deftest reduce-test
  (let [interpretator (fn [ef & args]
                        (let [continuation      (e/continuation ef)
                              effect-!>coeffect (constantly ::not-used-coeffect)]
                          (e/perform effect-!>coeffect continuation args)))
        str*          (fn [& args]
                        (eff
                          (! (effect :print args))
                          (apply str args)))
        with-reduced  (fn [acc v]
                        (if (= :done v)
                          (reduced v)
                          v))
        with-reduced* (fn [_ v]
                        (eff
                          (! [:print v])
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
                          (! (effect :print args))
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
