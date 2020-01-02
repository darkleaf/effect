(ns darkleaf.effect.util-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.util :as u]
   [clojure.test :as t]))

(t/deftest reduce!
  (let [my-reduce!    (fn [& args]
                        (let [continuation      (e/continuation u/reduce!)
                              effect-!>coeffect (constantly ::not-used-coeffect)]
                          (e/perform effect-!>coeffect continuation args)))
        str*          (fn [& args]
                        (eff
                          (! (effect [:print args]))
                          (apply str args)))
        with-reduced  (fn [_acc v]
                        (if (= :done v)
                          (reduced v)
                          v))
        with-reduced* (fn [_acc v]
                        (eff
                          (! (effect [:print v]))
                          (if (= :done v)
                            (reduced v)
                            v)))]
    (t/are [coll] (= (reduce str coll)
                     (my-reduce! str* coll)
                     (my-reduce! str coll))
      nil
      []
      [:a]
      [:a :b]
      [:a :b :c])
    (t/are [val coll] (= (reduce str val coll)
                         (my-reduce! str* val coll)
                         (my-reduce! str val coll))

      "acc" []
      "acc" [:a]
      "acc" [:a :b]
      "acc" [:a :b :c])
    (t/are [coll] (= (reduce with-reduced coll)
                     (my-reduce! with-reduced* coll)
                     (my-reduce! with-reduced coll))
      [:done]
      [1 :done]
      [1 2 3 :done 4 5])))

(t/deftest mapv!
  (let [my-mapv! (fn [& args]
                   (let [continuation      (e/continuation u/mapv!)
                         effect-!>coeffect (constantly ::not-used-coeffect)]
                     (e/perform effect-!>coeffect continuation args)))
        str*     (fn [& args]
                   (eff
                     (! (effect [:print args]))
                     (apply str args)))]
    (t/are [colls] (= (apply mapv str colls)
                      (apply my-mapv! str* colls)
                      (apply my-mapv! str colls))
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
