(ns darkleaf.effect.middleware.state
  (:require
   [darkleaf.generator.proto :as p]))

(defn wrap-state [f*]
  (fn [initial & args]
    (let [state   (atom initial)
          gen     (apply f* args)
          process #(when (not (p/-done? gen))
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
                         (recur))))]
      (process)
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
          (process))
        (-throw [_ throwable]
          (p/-throw gen throwable)
          (process))
        (-return [_ [new-context result]]
          (p/-return gen result)
          (process))))))
