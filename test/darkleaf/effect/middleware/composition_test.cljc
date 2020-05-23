(ns darkleaf.effect.middleware.composition-test
  (:require
   [clojure.test :as t]
   [darkleaf.effect.middleware.contract :as contract]
   [darkleaf.effect.middleware.context :as context]
   [darkleaf.effect.middleware.reduced :as reduced]
   [darkleaf.effect.core :as e :refer [! effect with-effects]]))

(t/deftest contract+reduced+context
  (let [ef           (fn []
                       (with-effects
                         (let [session (! (effect :session/get))]
                           (if (= :blocked (:user session))
                             (! (effect :control/return :unauthorized)))
                           (if (contains? session :user)
                             (! (effect :control/return :ok)))
                           (! (effect :session/update assoc :user :regular))
                           :ok)))
        contract     {'my/ef          {:args   (fn [] true)
                                       :return (fn [x] (= :ok x))}
                      :control/return {:effect (fn [x] (any? x))}
                      :session/get    {:effect   (fn [] true)
                                       :coeffect map?}
                      :session/update {:effect   (fn [f & args] (ifn? f))
                                       :coeffect map?}}
        continuation (-> ef
                         (e/continuation)
                         (contract/wrap-contract contract 'my/ef)
                         (reduced/wrap-reduced)
                         (context/wrap-context))

        handlers     {:control/return (fn [session x]
                                        [session (reduced x)])
                      :session/get    (fn [session]
                                        [session session])
                      :session/update (fn [session f & args]
                                        (let [new-session (apply f session args)]
                                          [new-session new-session]))}
        f            (fn [session]
                       (e/perform handlers continuation [session]))]
    (t/is (= [{:user :regular} :ok] (f {})))
    (t/is (= [{:user :blocked} :unauthorized] (f {:user :blocked})))
    (t/is (= [{:user :regular} :ok] (f {:user :regular})))))
