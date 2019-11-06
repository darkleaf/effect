(ns darkleaf.effect.runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [darkleaf.effect.core-test]))

(doo-tests 'darkleaf.effect.core-test)
