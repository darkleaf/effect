(ns darkleaf.effect.core
  (:refer-clojure :exclude [test mapv reduce])
  (:require
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i])
  #?(:cljs (:require-macros [darkleaf.effect.core :refer [eff]])))

(defn effect [x]
  (i/with-kind x :effect))

(defn ! [x]
  (case (i/kind x)
    :effect    x
    :coroutine x
    (i/with-kind [x] :wrapped)))

(defmacro ^{:style/indent :defn} eff [& body]
  `(i/with-kind
     (cr {! i/coeffect} ~@body)
     :coroutine))

(defn call [coroutine return coeffect]
  (let [val (i/with-coeffect coeffect coroutine)]
    (case (i/kind val)
      :effect [val (partial coroutine call return)]
      :coroutine (val call (partial coroutine call return) nil)
      :wrapped (recur coroutine return (first val))
      (return val))))

(defn done [x] [x nil])

(defn continuation [effn]
  (fn [args] ((apply effn args) call done nil)))

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
