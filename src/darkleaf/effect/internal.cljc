(ns darkleaf.effect.internal
  #?(:cljs (:require-macros [darkleaf.effect.internal :refer [<<-]])))

(defn kind [x]
  (-> x meta ::kind))

(defn with-kind [x kind]
  (vary-meta x assoc ::kind kind))

(defn exception? [x]
  (instance? #?(:clj Throwable, :cljs js/Error) x))

(declare ^:dynamic *coeffect*)

(defn coeffect []
  (if (exception? *coeffect*)
    (throw *coeffect*)
    *coeffect*))

(defn with-coeffect [coval coroutine]
  (binding [*coeffect* coval]
    (coroutine)))

(defmacro <<- [& body]
  `(->> ~@(reverse body)))
