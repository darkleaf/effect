(ns darkleaf.effect.middleware.reduced
  (:require
   [darkleaf.generator.proto :as p]))

(defn wrap-reduced [f*]
  (fn [& args]
    (let [gen (apply f* args)]
      (reify
        p/Generator
        (-done? [_]
          (p/-done? gen))
        (-value [_]
          (p/-value gen))
        (-next [_ covalue]
          (if (reduced? covalue)
            (p/-return gen @covalue)
            (p/-next gen covalue)))
        (-throw [_ throwable]
          (p/-throw gen throwable))
        (-return [_ result]
          (p/-return gen result))))))
