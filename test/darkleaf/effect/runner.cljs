(ns darkleaf.effect.runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [darkleaf.effect.core-test]
   [darkleaf.effect.script-test]
   [darkleaf.effect.middleware.context-test]
   [darkleaf.effect.middleware.log-test]
   [darkleaf.effect.middleware.reduced-test]))

(doo-tests 'darkleaf.effect.core-test
           'darkleaf.effect.script-test
           'darkleaf.effect.middleware.context-test
           'darkleaf.effect.middleware.log-test
           'darkleaf.effect.middleware.reduced-test)
