(ns darkleaf.effect.middleware.reduced)

(defn wrap-reduced [continuation]
  (when (some? continuation)
    (fn [coeffect]
      (if (reduced? coeffect)
        [@coeffect nil]
        (let [[effect continuation] (continuation coeffect)]
          [effect (wrap-reduced continuation)])))))
