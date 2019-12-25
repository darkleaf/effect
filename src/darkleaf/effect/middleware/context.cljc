(ns darkleaf.effect.middleware.context)

(defn wrap-context [continuation]
  (when (some? continuation)
    (fn [[context coeffect]]
      (let [[effect continuation] (continuation coeffect)]
        [[context effect] (wrap-context continuation)]))))
