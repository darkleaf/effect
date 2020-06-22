(ns darkleaf.effect.loom
  (:require
   [darkleaf.effect.internal :as i])
  (:import
   [java.lang Continuation ContinuationScope]
   [java.util WeakHashMap]))

(def scope (ContinuationScope. "effect"))

;; locking?
(def effects-registry (WeakHashMap.))
(def coeffects-registry (WeakHashMap.))

(defn effect [tag & args]
  (-> (cons tag args)
      (i/with-kind :effect)))

(defn ! [x]
  (case (i/kind x)
    :effect (let [cc (Continuation/getCurrentContinuation scope)]
              (assert cc)
              ;; atomic
              (.put effects-registry cc x)
              (Continuation/yield scope)
              (let [coeffect (.get coeffects-registry cc)]
                (if (i/throwable? coeffect)
                  (throw coeffect))
                coeffect))
    x))

(defmacro with-effects [& body]
  `(do ~@body))

(defn continuation [effn]
  (let [body (fn []
               (let [cc   (Continuation/getCurrentContinuation scope)
                     args (.get coeffects-registry cc)
                     res  (apply effn args)]
                 (.put effects-registry cc res)))
        run  (fn run [cc coeffect]
               ;; (if (i/throwable? coeffect)
               ;;   (throw coeffect))
               (.put coeffects-registry cc coeffect)
               (.run cc)
               (let [effect (.get effects-registry cc)]
                 (if (.isDone cc)
                   [effect nil]
                   [effect (partial run cc)])))]
    (fn [args]
      (run (Continuation. scope body) args))))

(defn- exec-effect
  ([handlers [tag & args]]
   (let [handler (get handlers tag)]
     (if-not (ifn? handler) (throw (ex-info "The effect handler is not a function"
                                            {:handler handler :tag tag})))
     (try
       (apply handler args)
       (catch Throwable error
         error))))
  ([handlers [tag & args] respond raise]
   (let [handler (get handlers tag)]
     (if-not (ifn? handler)
       (raise (ex-info "The effect handler is not a function"
                       {:handler handler :tag tag}))
       (apply handler (concat args [respond respond]))))))

(defn perform
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
                        (perform handlers continuation coeffect
                                 respond raise))
                      raise)))
     (catch Throwable error
       (raise error)))))
