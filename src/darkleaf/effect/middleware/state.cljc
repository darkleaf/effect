(ns darkleaf.effect.middleware.state
  (:require
   [darkleaf.generator.proto :as p]))

(defn- process [gen state]
  (when (not (p/-done? gen))
    (let [{:keys [tag args]} (p/-value gen)
          covalue (case tag
                    ::get    @state
                    ::put    (reset! state (first args))
                    ::gets   (let [[f & args] args]
                               (apply f @state args))
                    ::modify (let [[f & args] args]
                               (apply swap! state f args))
                    ::pass)]
      (when (not= ::pass covalue)
        (p/-next gen covalue)
        (recur gen state)))))

(defn wrap-state [f*]
  (fn [initial & args]
    (let [state  (atom initial)
          gen     (apply f* args)]
      (process gen state)
      (reify
        p/Generator
        (-done? [_]
          (p/-done? gen))
        (-value [_]
          (if (p/-done? gen)
            [@state (p/-value gen)]
            (p/-value gen)))
        (-next [_ covalue]
          (p/-next gen covalue)
          (process gen state))
        (-throw [_ throwable]
          (p/-throw gen throwable)
          (process gen state))
        (-return [_ [new-context result]]
          (p/-return gen result)
          (process gen state))))))
