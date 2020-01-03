(ns darkleaf.effect.util-test
  (:require
   [darkleaf.effect.core :as e :refer [! with-effects  effect]]
   [darkleaf.effect.util :as u]
   [clojure.test :as t]))

(defn- wrap-effect [f]
  (fn [& args]
    (with-effects
      (! (effect [:prn [:args args]]))
      (let [result (apply f args)]
        (! (effect [:prn [:result result]]))
        result))))

(defn- call [effn]
  (let [continuation      (e/continuation effn)
        effect-!>coeffect (constantly ::not-interesting)]
    (e/perform effect-!>coeffect continuation [])))

(t/deftest ->!
  (let [inc* (wrap-effect inc)
        dec* (wrap-effect dec)]
    (t/is (= 1
             (                       ->  0 inc  inc  dec)
             (call #(with-effects (u/->! 0 inc  inc  dec)))
             (call #(with-effects (u/->! 0 inc* inc* dec*)))
             (call #(with-effects (u/->! (inc* 0) inc* dec*)))))))

(t/deftest reduce!
  (let [str*          (wrap-effect str)
        with-reduced  (fn [_acc v]
                        (if (= :done v)
                          (reduced v)
                          v))
        with-reduced* (wrap-effect with-reduced)]
    (t/are [coll] (= (         reduce  str  coll)
                     (call #(u/reduce! str  coll))
                     (call #(u/reduce! str* coll)))
      nil
      []
      [:a]
      [:a :b]
      [:a :b :c])
    (t/are [val coll] (= (         reduce  str  val coll)
                         (call #(u/reduce! str  val coll))
                         (call #(u/reduce! str* val coll)))
      "acc" []
      "acc" [:a]
      "acc" [:a :b]
      "acc" [:a :b :c])
    (t/are [coll] (= (         reduce  with-reduced  coll)
                     (call #(u/reduce! with-reduced  coll))
                     (call #(u/reduce! with-reduced* coll)))
      [:done]
      [1 :done]
      [1 2 3 :done 4 5])))

(t/deftest mapv!
  (let [str* (wrap-effect str)]
    (t/are [colls] (= (       apply mapv    str  colls)
                      (call #(apply u/mapv! str  colls))
                      (call #(apply u/mapv! str* colls)))

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
