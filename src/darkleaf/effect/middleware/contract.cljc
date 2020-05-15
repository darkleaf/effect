(ns darkleaf.effect.middleware.contract)

(defn- check! [x contract path]
  (let [pred (get-in contract path)]
    (if-not (ifn? pred)
      (throw (ex-info "The predicate is wrong" {:pred pred :path path})))
    (if-not (pred x)
      (throw (ex-info "The value is mismatched by a predicate" {:actual x, :path path})))))

(defn- wrap-contract* [continuation contract coeffect-path]
  (fn [coeffect]
    (check! coeffect contract coeffect-path)
    (let [[effect continuation] (continuation coeffect)]
      (if (nil? continuation)
        (do (check! effect contract [:return])
            [effect nil])
        (let [get-tag (get contract :tag first)
              tag     (get-tag effect)]
          (check! effect contract [tag :effect])
          [effect (wrap-contract* continuation contract [tag :coeffect])])))))

(defn wrap-contract [continuation contract]
  (wrap-contract* continuation contract [:args]))
