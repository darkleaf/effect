(ns darkleaf.effect.middleware.contract)

(defn- check! [x contract path]
  (let [predicate (get-in contract path)
        kind      (last path)]
    (cond
      (not (ifn? predicate))
      (throw (ex-info "The predicate is not a function" {:predicate predicate :path path}))

      (and (= :args kind)
           (not (apply predicate x)))
      (throw (ex-info "The args are rejected by a predicate" {:args x :path path}))

      (and (= :return kind)
           (not (predicate x)))
      (throw (ex-info "The return value is rejected by a predicate" {:return x :path path}))

      (and (= :effect kind)
           (not (apply predicate x)))
      (throw (ex-info "The effect args are rejected by a predicate" {:args x :path path}))

      (and (= :coeffect kind)
           (not (predicate x)))
      (throw (ex-info "The coeffect is rejected by a predicate" {:coeffect x :path path})))))

(defn- wrap-contract* [continuation contract coeffect-path return-path]
  (fn [coeffect]
    (check! coeffect contract coeffect-path)
    (let [[effect continuation] (continuation coeffect)]
      (if (nil? continuation)
        (do (check! effect contract return-path)
            [effect nil])
        (let [[tag & args] effect]
          (check! args contract [tag :effect])
          [effect (wrap-contract* continuation contract [tag :coeffect] return-path)])))))

(defn wrap-contract [continuation contract fn-name]
  (wrap-contract* continuation contract [fn-name :args] [fn-name :return]))
