(ns darkleaf.effect.middleware.context-test
  (:require
   [darkleaf.effect.core :as e :refer [with-effects ! effect]]
   [darkleaf.effect.script :as script]
   [darkleaf.effect.middleware.context :as context]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(t/deftest state
  (let [ef (fn [x]
             (with-effects
               (! (effect :prn "hi"))
               [x
                (! (effect :update inc))
                (! (effect :update + 2))
                (! (effect :get))]))]
    (t/testing "intepretator"
      (let [continuation (-> ef
                             (e/continuation)
                             (context/wrap-context))
            handlers     {:prn    (fn [context msg]
                                    [context nil])
                          :get    (fn [context]
                                    [context context])
                          :update (fn [context f & args]
                                    (let [new-context (apply f context args)]
                                      [new-context new-context]))}
            initial      0
            f            (fn [x] (e/perform handlers continuation [initial [x]]))]
        (t/is (= [3 [0 1 3 3]]
                 (f 0)))))
    (t/testing "script"
      (let [continuation (e/continuation ef)
            script       [{:args [0]}
                          {:effect   [:prn "hi"]
                           :coeffect nil}
                          {:effect   [:update inc]
                           :coeffect 1}
                          {:effect   [:update + 2]
                           :coeffect 3}
                          {:effect   [:get]
                           :coeffect 3}
                          {:return [0 1 3 3]}]]
        (script/test continuation script)))
    (t/testing "script with applied middleware"
      (let [continuation (-> ef
                             (e/continuation)
                             (context/wrap-context))
            script       [{:args [0 [0]]}
                          {:effect   [:prn 0 "hi"]
                           :coeffect [0 nil]}
                          {:effect   [:update 0 inc]
                           :coeffect [1 1]}
                          {:effect   [:update 1 + 2]
                           :coeffect [3 3]}
                          {:effect   [:get 3]
                           :coeffect [3 3]}
                          {:return [3 [0 1 3 3]]}]]
        (script/test continuation script)))))

(t/deftest exceptions
  (let [ef           (fn []
                       (with-effects
                         (! (effect :throw))))
        handlers     {:throw (fn [state]
                               (throw (ex-info "Test" {})))}
        continuation (-> ef
                         (e/continuation)
                         (context/wrap-context))
        f            (fn [] (e/perform handlers continuation [{} []]))]
    (t/is (thrown? ExceptionInfo (f)))))
