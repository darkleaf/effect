(ns darkleaf.effect.internal)

(defn kind [x]
  (-> x meta ::kind))

(defn with-kind [x kind]
  (vary-meta x assoc ::kind kind))

(declare ^:dynamic *coeffect*)

(defn coeffect []
  *coeffect*)

(defn with-coeffect [coval f]
  (binding [*coeffect* coval]
    (f)))
