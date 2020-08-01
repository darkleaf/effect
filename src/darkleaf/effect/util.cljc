(ns darkleaf.effect.util
  #?(:cljs (:require-macros [darkleaf.effect.util :refer [<<-]])))

(defmacro <<- [& body]
  `(->> ~@(reverse body)))

(defn throwable? [x]
  (instance? #?(:clj Throwable, :cljs js/Error) x))
