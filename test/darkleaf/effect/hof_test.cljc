(ns darkleaf.effect.hof-test
  (:require
   [darkleaf.effect.core :as e :refer [! eff effect]]
   [darkleaf.effect.hof :as hof]
   [clojure.test :as t]))

(t/deftest reduce!
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
                     (interpretator hof/reduce! str* coll)
                     (interpretator hof/reduce! str coll))
      nil
      []
      [:a]
      [:a :b]
      [:a :b :c])
    (t/are [val coll] (= (reduce str val coll)
                         (interpretator hof/reduce! str* val coll)
                         (interpretator hof/reduce! str val coll))

      "acc" []
      "acc" [:a]
      "acc" [:a :b]
      "acc" [:a :b :c])
    (t/are [coll] (= (reduce with-reduced coll)
                     (interpretator hof/reduce! with-reduced* coll)
                     (interpretator hof/reduce! with-reduced coll))
      [:done]
      [1 :done]
      [1 2 3 :done 4 5])))

(t/deftest mapv!
  (let [interpretator (fn [ef & args]
                        (let [continuation      (e/continuation ef)
                              effect-!>coeffect (constantly ::not-used-coeffect)]
                          (e/perform effect-!>coeffect continuation args)))
        str*          (fn [& args]
                        (eff
                          (! (effect [:print args]))
                          (apply str args)))]
    (t/are [colls] (= (apply mapv str colls)
                      (apply interpretator hof/mapv! str* colls)
                      (apply interpretator hof/mapv! str colls))
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
