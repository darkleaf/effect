(ns darkleaf.effect.middleware.contract
  (:require
   [darkleaf.effect.util :as u]
   [darkleaf.generator.proto :as p]))

(defn wrap-contract [f* contract fn-name]
  (letfn [(check-args [args]
            (let [path      [fn-name :args]
                  predicate (u/getx-in contract path)]
              (when-not (apply predicate args)
                (throw (ex-info "The args are rejected by a predicate"
                                {:args args :path path})))))
          (check-effect [gen]
            (if (p/-done? gen)
              (-check-return gen)
              (-check-effect gen)))
          (check-coeffect [gen coeffect]
            (when-not (p/-done? gen)
              (let [{:keys [tag]} (p/-value gen)
                    path          [tag :coeffect]
                    predicate     (u/getx-in contract path)]
                (when-not (predicate coeffect)
                  (throw (ex-info "The coeffect is rejected by a predicate"
                                  {:coeffect coeffect :path path}))))))
          (-check-effect [gen]
            (let [{:keys [tag args]} (p/-value gen)
                  path               [tag :effect]
                  predicate          (u/getx-in contract path)]
              (when-not (apply predicate args)
                (throw (ex-info "The effect args are rejected by a predicate"
                                {:args args :path path})))))
          (-check-return [gen]
            (let [value     (p/-value gen)
                  path      [fn-name :return]
                  predicate (u/getx-in contract path)]
              (when-not (predicate value)
                (throw (ex-info "The return value is rejected by a predicate"
                                {:return value :path path})))))]
    (fn [& args]
      (check-args args)
      (let [gen (apply f* args)]
        (check-effect gen)
        (reify
          p/Generator
          (-done? [_]
            (p/-done? gen))
          (-value [_]
            (p/-value gen))
          (-next [_ covalue]
            (check-coeffect gen covalue)
            (p/-next gen covalue)
            (check-effect gen))
          (-throw [_ throwable]
            (p/-throw gen throwable)
            (check-effect gen))
          (-return [_ result]
            (p/-return gen result)
            (check-effect gen)))))))

