(ns darkleaf.effect.core
  (:require
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i]))

(defn ! [x]
  (if (vector? x)
    (i/with-kind x ::effect)
    x))

(defmacro ^{:style/indent :defn} eff [& body]
  `(i/with-kind
     (cr {! i/coeffect} ~@body)
     ::coroutine))

(defn interpret [effn & args]
  (letfn [(->continuation [stack]
            (fn continuation [coeffect]
              (loop [stack    stack
                     coeffect coeffect]
                (if (empty? stack)
                  [coeffect nil]
                  (let [coroutine (peek stack)
                        val  (i/with-coeffect coeffect coroutine)]
                    (case (i/kind val)
                      ::effect       [val (->continuation stack)]
                      ::coroutine (recur (conj stack val) nil)
                      ;; nested coroutine is finished
                      (recur (pop stack) val)))))))]
    ((->continuation (list (apply effn args))) nil)))
