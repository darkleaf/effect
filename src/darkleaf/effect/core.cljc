(ns darkleaf.effect.core
  (:refer-clojure :exclude [test])
  (:require
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i]
   [clojure.test :as t])
  #?(:cljs (:require-macros [darkleaf.effect.core :refer [eff]])))

(defn ! [x]
  (if (vector? x)
    (i/with-kind x ::effect)
    x))

(defmacro ^{:style/indent :defn} eff [& body]
  `(i/with-kind
     (cr {! i/coeffect} ~@body)
     ::coroutine))

(defn loop-factory [effn & args]
  (let [->continuation (fn ->continuation [stack]
                         (fn continuation [coeffect]
                           (loop [stack    stack
                                  coeffect coeffect]
                             (if (empty? stack)
                               [coeffect nil]
                               (let [coroutine (peek stack)
                                     val       (i/with-coeffect coeffect coroutine)]
                                 (case (i/kind val)
                                   ::effect    [val (->continuation stack)]
                                   ::coroutine (recur (conj stack val) nil)
                                   ;; coroutine is finished
                                   (recur (pop stack) val)))))))
        coroutine      (apply effn args)
        stack          (list coroutine)
        continuation   (->continuation stack)
        coeffect       ::not-used]
    (continuation coeffect)))

(defn- test-first-item [{:keys [report effn]} {:keys [args]}]
  (let [[effect continuation] (apply loop-factory effn args)]
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
  (reduce test-middle-item ctx items))

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

(defn test [effn script]
  {:pre [(fn? effn)
         (<= 2 (count script))]}
  (let [first-item   (first script)
        middle-items (-> script rest butlast)
        last-item    (last script)]
    (-> {:effn effn, :report {:type :pass}}
        (test-first-item first-item)
        (test-middle-items middle-items)
        (test-last-item last-item)
        :report
        (t/do-report))))
