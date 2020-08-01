(ns darkleaf.effect.middleware.context
  (:require [darkleaf.effect.util :as u]))

(defn- wrap-context* [continuation fallback-context]
  (fn [raw-coeffect]
    (let [[context coeffect]    (if (u/throwable? raw-coeffect)
                                  [fallback-context raw-coeffect]
                                  raw-coeffect)
          [effect continuation] (continuation coeffect)]
      (if (nil? continuation)
        [[context effect] nil]
        (let [[tag & args] effect
              effect       (cons tag (cons context args))]
          [effect (wrap-context* continuation context)])))))

(defn wrap-context [continuation]
  (wrap-context* continuation nil))
