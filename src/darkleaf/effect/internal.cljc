(ns darkleaf.effect.internal
  (:require
   [darkleaf.effect.util :as u]))

(defn kind [x]
  (-> x meta ::kind))

(defn with-kind [x kind]
  (vary-meta x assoc ::kind kind))

(defn wrap-return-value [x]
  (with-kind [x] :return-value))

(defn unwrap-value [x]
  (first x))

(declare ^:dynamic *coeffect*)

(defn coeffect []
  (if (u/throwable? *coeffect*)
    (throw *coeffect*)
    *coeffect*))

(defn with-coeffect [coval coroutine]
  (binding [*coeffect* coval]
    (coroutine)))
