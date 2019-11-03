(ns darkleaf.effect.core-test
  (:require
   [darkleaf.effect.core :as e]
   [clojure.test :as t]))

(t/deftest trivial
  (let [effect-!>coeffect (fn [effect] (throw (ex-info "Should not be used" {:effect effect})))
        ef                (fn [x]
                            (e/eff
                              x))
        result            (loop [[effect continuation] (e/interpret ef 42)]
                            (if (nil? continuation)
                              effect
                              (recur (continuation (effect-!>coeffect effect)))))]
    (t/is (= 42 result))))

(t/deftest simple
  (let [effect-!>coeffect (fn [effect]
                            (case (first effect)
                              :read "John"))
        ef                (fn [x]
                            (e/eff
                              (str x " " (e/! [:read]))))
        result            (loop [[effect continuation] (e/interpret ef "Hi!")]
                            (if (nil? continuation)
                              effect
                              (recur (continuation (effect-!>coeffect effect)))))]
    (t/is (= "Hi! John" result))))

(t/deftest stack
  (let [effect-!>coeffect (fn [[kind value :as effect] i]
                            (cond
                              (and (= 0 i)
                                   (= :prn kind)
                                   (= "foo" value))
                              nil

                              (and (= 1 i)
                                   (= :prn kind)
                                   (= 0 value))
                              nil

                              (and (= 2 i)
                                   (= :prn kind)
                                   (= 1 value))
                              nil

                              :else
                              (throw (ex-info "Unknown effect" {:effect effect, :i i}))))
        ef1               (fn []
                            (e/eff
                              (doseq [i (range 2)]
                                (e/! [:prn i]))))
        ef2               (fn [x]
                            (e/eff
                              (e/! [:prn x])
                              (e/! (ef1))
                              :ok))
        ef                (fn []
                            (e/eff
                              (e/! (ef2 "foo"))))

        result            (loop [[effect continuation] (e/interpret ef)
                                 i                 0]
                            (if (nil? continuation)
                              effect
                              (recur (continuation (effect-!>coeffect effect i))
                                     (inc i))))]
    (t/is (= :ok result))))
