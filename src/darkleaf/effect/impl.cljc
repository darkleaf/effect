(ns darkleaf.effect.impl
  (:require
   [darkleaf.generator.proto :as p]))

(defrecord Effect [tag args])

(defn wrap-pass-values [f*]
  (fn [& args]
    (let [gen        (apply f* args)
          pass-value #(when-not (p/-done? gen)
                        (let [v (p/-value gen)]
                          (when-not (instance? Effect v)
                            (p/-next gen v)
                            (recur))))]
      (reify
        p/Generator
        (-done? [_] (p/-done? gen))
        (-value [_] (p/-value gen))
        (-next [_ covalue]
          (p/-next gen covalue)
          (pass-value))
        (-throw [_ throwable]
          (p/-throw gen throwable)
          (pass-value))
        (-return [_ result]
          (p/-return gen result)
          (pass-value))))))
