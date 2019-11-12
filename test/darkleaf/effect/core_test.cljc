(ns darkleaf.effect.core-test
  (:require
   [darkleaf.effect.core :as e :refer [eff !]]
   [clojure.test :as t]))

(t/deftest simple-use-case
  (let [effect-!>coeffect (fn [[tag value :as effect]]
                            (case tag
                              :read "John"))
        ef                (fn [x]
                            (eff
                              (str x " " (! [:read]))))
        result            (loop [[effect continuation] (e/loop-factory ef "Hi!")]
                            (if (nil? continuation)
                              effect
                              (recur (continuation (effect-!>coeffect effect)))))]
    (t/is (= "Hi! John" result))))

(t/deftest stack-use-case
  (let [effect-!>coeffect (fn [[tag value :as effect]]
                            (case tag
                              :prn  nil
                              :read "input string"))
        nested-ef         (fn [x]
                            (eff
                              (! [:prn x])
                              (! [:read])))
        ef                (fn [x]
                            (eff
                              (! [:prn :ef])
                              (! (nested-ef x))))
        result            (loop [[effect continuation] (e/loop-factory ef "some val")]
                            (if (nil? continuation)
                              effect
                              (recur (continuation (effect-!>coeffect effect)))))]
    (t/is (= "input string" result))))

(t/deftest script
  (let [ef (fn [x]
             (eff
               (! [:some-eff x])))]
    (t/testing "correct"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:return :other-value}]]
        (e/test ef script)))
    (t/testing "final-effect"
      (let [script [{:args [:value]}
                    {:final-effect [:some-eff :value]}]]
        (e/test ef script)))
    (t/testing "wrong effect"
      (let [script [{:args [:value]}
                    {:effect   [:wrong]
                     :coeffect :other-value}
                    {:return :other-value}]
            report (with-redefs [t/do-report identity]
                     (e/test ef script))]
        (t/is (= {:type     :fail
                  :expected [:wrong]
                  :actual   [:some-eff :value]
                  :message  "Wrong effect"}
                 report))))
    (t/testing "wrong return"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:return :wrong}]
            report (with-redefs [t/do-report identity]
                     (e/test ef script))]
        (t/is (= {:type     :fail
                  :expected :wrong
                  :actual   :other-value
                  :message  "Wrong return"}
                 report))))
    (t/testing "wrong final-effect"
      (let [script [{:args [:value]}
                    {:final-effect [:wrong]}]
            report (with-redefs [t/do-report identity]
                     (e/test ef script))]
        (t/is (= {:type     :fail,
                  :expected [:wrong],
                  :actual   [:some-eff :value],
                  :message  "Wrong final effect"}
                 report))))
    (t/testing "extra effect"
      (let [script [{:args [:value]}
                    {:return :wrong}]
            report (with-redefs [t/do-report identity]
                     (e/test ef script))]
        (t/is (=  {:type     :fail
                   :expected nil
                   :actual   [:some-eff :value]
                   :message  "Extra effect"}
                  report))))
    (t/testing "missed effect"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:effect   [:extra-eff :value]
                     :coeffect :some-value}
                    {:return :some-other-value}]
            report (with-redefs [t/do-report identity]
                     (e/test ef script))]
        (t/is (= {:type     :fail
                  :expected [:extra-eff :value]
                  :actual   nil
                  :message  "Misssed effect"}
                 report))))))

(t/deftest trivial-script
  (let [ef     (fn [x]
                 (eff
                   x))
        script [{:args [:value]}
                {:return :value}]]
      (e/test ef script)))

(t/deftest fallback-script
  (let [ef     (fn [x]
                 (eff
                   (! (inc x))
                   (! [:eff])
                   (! (dec x))))
        script [{:args [0]}
                {:effect   [:eff]
                 :coeffect nil}
                {:return -1}]]
    (e/test ef script)))

(t/deftest stack-script
  (let [ef1    (fn []
                 (eff
                   (doseq [i (range 2)]
                     (! [:prn i]))))
        ef2    (fn [x]
                 (eff
                   (! [:prn x])
                   (! (ef1))
                   :ok))
        ef     (fn []
                 (eff
                   (! (ef2 "foo"))))
        script [{:args []}
                {:effect   [:prn "foo"]
                 :coeffect nil}
                {:effect   [:prn 0]
                 :coeffect nil}
                {:effect   [:prn 1]
                 :coeffect nil}
                {:return :ok}]]
    (e/test ef script)))

(t/deftest effect-as-value-script
  (let [effect [:prn 1]
        ef     (fn []
                 (eff
                   (! effect)
                   (! (assoc effect 1 2))))
        script [{:args []}
                {:effect   effect
                 :coeffect nil}
                {:effect  [:prn 2]
                 :coeffet nil}
                {:return nil}]]
    (e/test ef script)))

#?(:cljs
   (t/deftest async-example
     (let [effect-!>coeffect (fn [[tag value :as effect]]
                               (case tag
                                 :read  "value"
                                 :print nil))
           ef                (fn []
                               (eff
                                (loop [values []
                                       i      0]
                                  (if (= i 2)
                                    values
                                    (let [value (! [:read])]
                                      (! [:print value])
                                      (recur (conj values value)
                                             (inc i)))))))
           main-loop (fn main-loop [[effect continuation] callback]
                       (if (nil? continuation)
                         (callback effect)
                         (js/setTimeout #(main-loop (continuation (effect-!>coeffect effect))
                                                    callback)
                                        0)))]
       (t/async done
                (main-loop (e/loop-factory ef)
                           (fn [result]
                             (t/is (= ["value" "value"] result))
                             (done)))))))

(t/deftest maybe-example
  (let [effect-!>coeffect (fn [[tag value :as effect]]
                            (case tag
                              :maybe (if (nil? value)
                                       (reduced nil)
                                       value)))
        ef                (fn [x]
                            (eff
                              (+ 5 (! [:maybe x]))))]
    (t/testing "interpretator"
      (let [interpretator (fn [x]
                            (loop [[effect continuation] (e/loop-factory ef x)]
                              (if (nil? continuation)
                                effect
                                (let [coeffect (effect-!>coeffect effect)]
                                  (if (reduced? coeffect)
                                    (unreduced coeffect)
                                    (recur (continuation coeffect)))))))]
        (t/is (= 6 (interpretator 1)))
        (t/is (= nil (interpretator nil)))))
    (t/testing "script"
      (t/testing :just
        (let [script [{:args [1]}
                      {:effect   [:maybe 1]
                       :coeffect 1}
                      {:return 6}]]
          (e/test ef script)))
      (t/testing :nothing
        (let [script [{:args [nil]}
                      {:final-effect [:maybe nil]}]]
          (e/test ef script))))))

(t/deftest state-example
  (let [effect-!>coeffect (fn [state [tag f & args :as effect]]
                            (case tag
                              :state/get    [state state]
                              :state/update (let [new-state (apply f state args)]
                                              [new-state new-state])
                              :io/print     [state nil]))
        ef                (fn []
                            (eff
                              (! [:io/print "hi"])
                              [(! [:state/update inc])
                               (! [:state/update + 2])
                               (! [:state/get])]))
        result            (loop [state                 0
                                 [effect continuation] (e/loop-factory ef)]
                            (if (nil? continuation)
                              effect
                              (let [[state coeffect] (effect-!>coeffect state effect)]
                                (recur state (continuation coeffect)))))]
    (t/is (= [1 3 3] result))))

(t/deftest reduce-test
  (let [interpretator (fn [ef & args]
                        (loop [[effect continuation] (apply e/loop-factory ef args)]
                          (if (nil? continuation)
                            effect
                            (recur (continuation ::not-used-coeffect)))))
        str*          (fn [& args]
                        (eff
                          (! [:print args])
                          (apply str args)))
        with-reduced (fn [acc v]
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
                        (loop [[effect continuation] (apply e/loop-factory ef args)]
                          (if (nil? continuation)
                            effect
                            (recur (continuation ::not-used-coeffect)))))
        str*          (fn [& args]
                        (eff
                          (! [:print args])
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
