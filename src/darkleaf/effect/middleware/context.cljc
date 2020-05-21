(ns darkleaf.effect.middleware.context)

(defn wrap-context [continuation]
  (fn [[context coeffect]]
    (let [[effect continuation] (continuation coeffect)]
      (if (nil? continuation)
        [[context effect] nil]
        (let [[tag & args] effect]
          [(cons tag (cons context args)) (wrap-context continuation)])))))
