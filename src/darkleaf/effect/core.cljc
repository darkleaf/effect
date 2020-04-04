(ns darkleaf.effect.core
  (:refer-clojure :exclude [test mapv reduce])
  (:require
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i])
  #?(:cljs (:require-macros [darkleaf.effect.core :refer [with-effects]])))

(defn effect [x]
  (i/with-kind x :effect))

(defn ! [x]
  (case (i/kind x)
    :effect    x
    :coroutine x
    (i/with-kind [x] :wrapped)))

(defmacro ^{:style/indent :defn} with-effects [& body]
  `(i/with-kind
     (cr {! i/coeffect} ~@body)
     :coroutine))

(defn- update-head [coll f & args]
  (if (seq coll)
    (-> coll
        (pop)
        (conj (apply f (peek coll) args)))
    coll))

(defn- stack->continuation [stack]
  (fn [coeffect]
    (loop [stack    (update-head stack (fn clone [mutable-coroutine]
                                         (mutable-coroutine identity)))
           coeffect coeffect]
      (if (empty? stack)
        [coeffect nil]
        (let [coroutine (peek stack)
              val       (i/with-coeffect coeffect coroutine)]
          (case (i/kind val)
            :effect    [val (stack->continuation stack)]
            :coroutine (recur (conj stack val) ::not-used)
            :wrapped   (recur stack (first val))
            ;; coroutine is finished
            (recur (pop stack) val)))))))

(defn continuation [effn]
  (fn [args]
    (let [coroutine (apply effn args)
          stack     (list coroutine)
          cont      (stack->continuation stack)
          coeffect  ::not-used]
      (cont coeffect))))

(defn- wrap-exception-as-value [f]
  (fn effect-!>coeffect
    ([effect]
     (try
       (f effect)
       (catch #?(:clj Throwable, :cljs js/Error) error
         error)))
    ([effect respond raise]
     (f effect respond respond))))

(defn- perform-impl
  ([effect-!>coeffect continuation coeffect-or-args]
   (loop [[effect continuation] (continuation coeffect-or-args)]
     (if (nil? continuation)
       effect
       (recur (continuation (effect-!>coeffect effect))))))
  ([effect-!>coeffect continuation coeffect-or-args respond raise]
   (try
     (let [[effect continuation] (continuation coeffect-or-args)]
       (if (nil? continuation)
         (respond effect)
         (effect-!>coeffect effect
                            (fn [coeffect]
                              (perform-impl effect-!>coeffect continuation coeffect
                                            respond raise))
                            raise)))
     (catch #?(:clj Throwable, :cljs js/Error) error
       (raise error)))))

(defn perform
  ([effect-!>coeffect continuation effect-or-args]
   (perform-impl (wrap-exception-as-value effect-!>coeffect)
                 continuation effect-or-args))
  ([effect-!>coeffect continuation effect-or-args respond raise]
   (perform-impl (wrap-exception-as-value effect-!>coeffect)
                 continuation effect-or-args
                 respond raise)))
