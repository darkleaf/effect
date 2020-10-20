(ns darkleaf.effect.impl
  (:require
   [darkleaf.generator.proto :as p]))

(defrecord Effect [tag args])

(defn- pass-value [gen]
  (when-not (p/-done? gen)
    (let [v (p/-value gen)]
      (when-not (instance? Effect v)
        (p/-next gen v)
        (recur gen)))))

(defn wrap-pass-values [gen]
  (reify
    p/Generator
    (-done? [_] (p/-done? gen))
    (-value [_] (p/-value gen))
    (-next [_ covalue]
      (p/-next gen covalue)
      (pass-value gen))
    (-throw [_ throwable]
      (p/-throw gen throwable)
      (pass-value gen))
    (-return [_ result]
      (p/-return gen result)
      (pass-value gen))))
