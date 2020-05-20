(ns darkleaf.effect.middleware.contract)

(defprotocol Matcher
  :extend-via-metadata true
  (matcher-report [matcher actual]))

(defn- check! [x contract path]
  (let [matcher (get-in contract path)]
    (if-not (satisfies? Matcher matcher)
      (throw (ex-info "The matcher is wrong" {::matcher matcher ::path path})))
    (if-let [report (matcher-report matcher x)]
      (throw (ex-info "The value is mismatched by a matcher" (assoc report ::path path))))))

(defn- wrap-contract* [continuation fn-name contract coeffect-path]
  (fn [coeffect]
    (check! coeffect contract coeffect-path)
    (let [[effect continuation] (continuation coeffect)]
      (if (nil? continuation)
        (do (check! effect contract [fn-name :return])
            [effect nil])
        (let [get-tag (get contract :tag first)
              tag     (get-tag effect)]
          (check! effect contract [tag :effect])
          [effect (wrap-contract* continuation fn-name contract [tag :coeffect])])))))

(defn wrap-contract [continuation fn-name contract]
  (wrap-contract* continuation fn-name contract [fn-name :args]))

(extend-protocol Matcher
  #?(:clj clojure.lang.Fn, :cljs function)
  (matcher-report [matcher actual]
    (if-not (matcher actual)
      {::actual actual})))
