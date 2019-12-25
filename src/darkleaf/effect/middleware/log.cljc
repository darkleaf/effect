(ns darkleaf.effect.middleware.log)

(def ^:private empty-log #?(:clj  clojure.lang.PersistentQueue/EMPTY
                            :cljs cljs.core/PersistentQueue.EMPTY))

(defn wrap-log [continuation]
  (let [log (-> continuation
                meta
                (get ::log empty-log))]
    (fn [coeffect]
      (if (= ::suspend coeffect)
        [[::suspended log] nil]
        (let [[effect continuation] (continuation coeffect)
              log                   (conj log {:coeffect    coeffect
                                               :next-effect effect})]
          (if (some? continuation)
            [effect (-> continuation
                        (vary-meta assoc ::log log)
                        wrap-log)]
            [[::result effect log] nil]))))))

(defn resume [continuation log]
  (if (seq log)
    (let [{:keys [coeffect next-effect]} (peek log)
          [effect continuation]          (continuation coeffect)]
      (if (not= next-effect effect)
        (throw (ex-info "Unexpected effect" {:expected next-effect
                                             :actual   effect})))
      (recur continuation (pop log)))
    continuation))
