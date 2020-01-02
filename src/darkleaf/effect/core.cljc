(ns darkleaf.effect.core
  (:refer-clojure :exclude [test mapv reduce])
  (:require
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i])
  #?(:cljs (:require-macros [darkleaf.effect.core :refer [break]])))

(defn effect [x]
  (i/with-kind x :effect))

(defn ! [x]
  (case (i/kind x)
    :effect    x
    :coroutine x
    (i/with-kind [x] :wrapped)))

(defmacro ^{:style/indent :defn} break [& body]
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
    (loop [stack    stack
           coeffect coeffect]
      (if (empty? stack)
        [coeffect nil]
        (let [stack     (update-head stack (fn clone [mutable-coroutine]
                                             (mutable-coroutine identity)))
              coroutine (peek stack)
              val       (i/with-coeffect coeffect coroutine)]
          (case (i/kind val)
            :effect    [val (stack->continuation stack)]
            :coroutine (recur (conj stack val) nil)
            :wrapped   (recur  stack (first val))
            ;; coroutine is finished
            (recur (pop stack) val)))))))

(defn continuation [effn]
  (fn [args]
    (let [coroutine (apply effn args)
          stack     (list coroutine)
          cont      (stack->continuation stack)
          coeffect  ::not-used]
      (cont coeffect))))

(defn perform
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
                              (perform effect-!>coeffect continuation coeffect
                                       respond raise))
                            raise)))
     (catch #?(:clj java.lang.Throwable, :cljs js/Error) error
       (raise error)))))
