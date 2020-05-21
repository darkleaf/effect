(ns darkleaf.effect.core
  (:refer-clojure :exclude [test mapv reduce])
  (:require
   [cloroutine.core :refer [cr]]
   [darkleaf.effect.internal :as i])
  #?(:cljs (:require-macros [darkleaf.effect.core :refer [with-effects]])))

(defn effect [tag & args]
  (-> (cons tag args)
      (i/with-kind :effect)))

(defn ! [x]
  x)

(defmacro with-effects [& body]
  `(i/with-kind
     (cr {! i/coeffect}
         (i/wrap-return-value
          (do ~@body)))
     :coroutine))

(defn- update-head [coll f & args]
  (if (seq coll)
    (-> coll
        (pop)
        (conj (apply f (peek coll) args)))
    coll))

(defn- clone-coroutine [coroutine]
  (coroutine identity))

(defn- stack->continuation [stack]
  (fn [coeffect]
    (loop [stack    (update-head stack clone-coroutine)
           coeffect coeffect]
      (if (empty? stack)
        [coeffect nil]
        (let [coroutine (peek stack)
              val       (i/with-coeffect coeffect coroutine)]
          (case (i/kind val)
            :effect       [val (stack->continuation stack)]
            :coroutine    (recur (conj stack val) ::not-used)
            :return-value (recur (pop stack) (i/unwrap-value val))
            (recur stack val)))))))

(defn continuation [effn]
  (fn [args]
    (let [coroutine (apply effn args)
          stack     (list coroutine)
          cont      (stack->continuation stack)
          coeffect  ::not-used]
      (cont coeffect))))

(defn- exec-effect
  ([handlers [tag & args]]
   (let [handler (get handlers tag)]
     (if-not (ifn? handler) (throw (ex-info "The effect handler is not a function"
                                            {:handler handler :tag tag})))
     (try
       (apply handler args)
       (catch #?(:clj Throwable, :cljs js/Error) error
         error))))
  ([handlers [tag & args] respond raise]
   (let [handler (get handlers tag)]
     (if-not (ifn? handler)
       (raise (ex-info "The effect handler is not a function"
                       {:handler handler :tag tag}))
       (apply handler (concat args [respond respond]))))))

(defn- perform-impl
  ([handlers continuation coeffect-or-args]
   (loop [[effect continuation] (continuation coeffect-or-args)]
     (if (nil? continuation)
       effect
       (recur (continuation (exec-effect handlers effect))))))
  ([handlers continuation coeffect-or-args respond raise]
   (try
     (let [[effect continuation] (continuation coeffect-or-args)]
       (if (nil? continuation)
         (respond effect)
         (exec-effect handlers effect
                      (fn [coeffect]
                        (perform-impl handlers continuation coeffect
                                      respond raise))
                      raise)))
     (catch #?(:clj Throwable, :cljs js/Error) error
       (raise error)))))

(defn perform
  ([handlers continuation effect-or-args]
   (perform-impl handlers
                 continuation effect-or-args))
  ([handlers continuation effect-or-args respond raise]
   (perform-impl handlers
                 continuation effect-or-args
                 respond raise)))
