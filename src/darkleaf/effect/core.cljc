(ns darkleaf.effect.core
  (:refer-clojure :exclude [test mapv reduce])
  (:require
   [clojure.core :as c]
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i]
   [clojure.test :as t])
  #?(:cljs (:require-macros [darkleaf.effect.core :refer [eff]])))

(defn ! [x]
  (cond
    (vector? x)
    (i/with-kind x ::effect)

    (= ::coroutine (i/kind x))
    x

    :else
    (i/with-kind [x] ::wrapped)))

(defmacro ^{:style/indent :defn} eff [& body]
  `(i/with-kind
     (cr {! i/coeffect} ~@body)
     ::coroutine))

(defn- stack->continuation [stack]
  (fn [coeffect]
    (loop [stack    stack
           coeffect coeffect]
      (if (empty? stack)
        [coeffect nil]
        (let [coroutine (peek stack)
              val       (i/with-coeffect coeffect coroutine)]
          (case (i/kind val)
            ::effect    [val (stack->continuation stack)]
            ::coroutine (recur (conj stack val) nil)
            ::wrapped   (recur  stack (first val))
            ;; coroutine is finished
            (recur (pop stack) val)))))))

(defn continuation [effn]
  (fn [args]
    (let [coroutine (apply effn args)
          stack     (list coroutine)
          cont      (stack->continuation stack)
          coeffect  ::not-used]
      (cont coeffect))))

(defn wrap-reduced [continuation]
  (when (some? continuation)
    (fn [coeffect]
      (if (reduced? coeffect)
        [@coeffect nil]
        (let [[effect continuation] (continuation coeffect)]
          [effect (wrap-reduced continuation)])))))

(defn wrap-context [continuation]
  (when (some? continuation)
    (fn [[context coeffect]]
      (let [[effect continuation] (continuation coeffect)]
        [[context effect] (wrap-context continuation)]))))

(defn wrap-suspend [continuation]
  (let [log (-> continuation
                meta
                (get ::log #?(:clj  clojure.lang.PersistentQueue/EMPTY
                              :cljs cljs.core/PersistentQueue.EMPTY)))]
    (fn [coeffect]
      (if (= ::suspend coeffect)
        [[::suspended log] nil]
        (let [[effect continuation] (continuation coeffect)]
          (if (some? continuation)
            [effect (-> continuation
                        (vary-meta assoc ::log (conj log {:coeffect    coeffect
                                                          :next-effect effect}))
                        wrap-suspend)]
            [[::done effect] nil]))))))

(defn resume [continuation log]
  (if (seq log)
    (let [{:keys [coeffect next-effect]} (peek log)
          [effect continuation]          (continuation coeffect)]
      (if (not= next-effect effect)
        (throw (ex-info "Unexpected effect" {:expected next-effect
                                             :actual   effect})))
      (recur continuation (pop log)))
    continuation))

(defn perform
  ([effect-!>coeffect continuation coeffect-or-args]
   (loop [[effect continuation] (continuation coeffect-or-args)]
     (if (nil? continuation)
       effect
       (recur (continuation (effect-!>coeffect effect))))))
  ([effect-!>coeffect continuation coeffect-or-args respond raise]
   (let [[effect continuation] (continuation coeffect-or-args)]
     (if (nil? continuation)
       (respond effect)
       (effect-!>coeffect effect
                          (fn [coeffect]
                            (perform effect-!>coeffect continuation coeffect
                                     respond raise))
                          raise)))))

(defn- test-first-item [{:keys [report continuation]} {:keys [args]}]
  (let [[effect continuation] (continuation args)]
    {:report        report
     :actual-effect effect
     :continuation  continuation}))

(defn- test-middle-item [{:keys [report actual-effect continuation]} {:keys [effect coeffect]}]
  (cond
    (not= :pass (:type report))
    {:report report}

    (nil? continuation)
    {:report {:type     :fail
              :expected effect
              :actual   nil
              :message  "Misssed effect"}}

    (not= effect actual-effect)
    {:report {:type     :fail
              :expected effect
              :actual   actual-effect
              :message  "Wrong effect"}}

    :else
    (let [[actual-effect continuation] (continuation coeffect)]
      {:report        report
       :actual-effect actual-effect
       :continuation  continuation})))

(defn- test-middle-items [ctx items]
  (c/reduce test-middle-item ctx items))

(defn- test-last-item [{:keys [report actual-effect continuation]}
                       {:keys [return final-effect]}]
  (cond
    (not= :pass (:type report))
    {:report report}

    (and (some? final-effect)
         (= final-effect actual-effect))
    {:report report}

    (some? final-effect)
    {:report {:type     :fail
              :expected final-effect
              :actual   actual-effect
              :message  "Wrong final effect"}}

    (some? continuation)
    {:report {:type     :fail
              :expected nil
              :actual   actual-effect
              :message  "Extra effect"}}

    (not= return actual-effect)
    {:report {:type     :fail
              :expected return
              :actual   actual-effect
              :message  "Wrong return"}}

    :else
    {:report report}))

(defn test* [continuation script]
  {:pre [(<= 2 (count script))]}
  (let [first-item   (first script)
        middle-items (-> script rest butlast)
        last-item    (last script)]
    (-> {:continuation continuation, :report {:type :pass}}
        (test-first-item first-item)
        (test-middle-items middle-items)
        (test-last-item last-item)
        :report)))

(defn test [continuation script]
  (-> (test* continuation script)
      (t/do-report)))

(defn reduce
  ([ef coll]
   (eff
     (case (count coll)
       0 (! (ef))
       1 (first coll)
       (! (reduce ef (first coll) (rest coll))))))
  ([ef val coll]
   (eff
     (loop [acc val
            coll coll]
       (cond
         (reduced? acc)
         (unreduced acc)

         (empty? coll)
         acc

         :else
         (recur (! (ef acc (first coll)))
                (rest coll)))))))

(defn mapv
  ([ef coll]
   (eff
     (let [reducer (fn [acc item]
                     (eff
                       (conj! acc (! (ef item)))))
           acc     (transient [])
           result  (! (reduce reducer acc coll))]
       (persistent! result))))
  ([ef coll & colls]
   (->> (apply map list coll colls)
        (mapv #(apply ef %)))))
