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
     ::continuation))

(defn interpret [effn & args]
  (letfn [(->callback [stack]
            (fn callback [coeffect]
              (loop [stack    stack
                     coeffect coeffect]
                (if (empty? stack)
                  [coeffect nil]
                  (let [cont (peek stack)
                        val  (i/with-coeffect coeffect cont)]
                    (case (i/kind val)
                      ::effect       [val (->callback stack)]
                      ::continuation (recur (conj stack val) nil)
                      ;; nested continuation is finished
                      (recur (pop stack) val)))))))]
    ((->callback (list (apply effn args))) nil)))
