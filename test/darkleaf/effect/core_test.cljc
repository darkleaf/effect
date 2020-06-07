(ns darkleaf.effect.core-test
  (:require
   [darkleaf.effect.core :as e :refer [with-effects ! effect]]
   [darkleaf.effect.script :as script]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(defn- next-tick [f & args]
  #?(:clj  (apply f args)
     :cljs (apply js/setTimeout f 0 args)))

(t/deftest simple
  (let [ef           (fn [k]
                       (with-effects
                         (int (* k (! (effect :random))))))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [handlers {:random (fn [] 0.1)}
            f        (fn [k]
                       (e/perform handlers continuation [k]))]
        (t/is (= 1 (f 10)))))
    (t/testing "script"
      (let [script [{:args [10]}
                    {:effect   [:random]
                     :coeffect 0.1}
                    {:return 1}]]
        (script/test continuation script)))))

(t/deftest simple-async
  (let [ef           (fn [k]
                       (with-effects
                         (int (* k (! (effect :random))))))
        handlers     {:random (fn [respond raise]
                                (next-tick respond 0.1))}
        continuation (e/continuation ef)
        f            (fn [x respond raise]
                       (e/perform handlers continuation [x]
                                  respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :respond kind))
               (t/is (= 1 value))
               (done))]
       (f 10 #(check :respond %) #(check :raise %))))))

(t/deftest missed-handler
  (let [ef           (fn [k]
                       (with-effects
                         (int (* k (! (effect :random))))))
        continuation (e/continuation ef)
        handlers     {}
        f            (fn [k]
                       (e/perform handlers continuation [k]))]
    (t/is (thrown-with-msg? ExceptionInfo #"The effect handler is not a function"
                            (f 10)))))

(t/deftest missed-handler-async
  (let [ef           (fn [k]
                       (with-effects
                         (int (* k (! (effect :random))))))
        continuation (e/continuation ef)
        handlers     {}
        f            (fn [k respond raise]
                       (e/perform handlers continuation [k]
                                  respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :raise kind))
               (t/is (= "The effect handler is not a function" (ex-message value)))
               (done))]
       (f 10 #(check :respond %) #(check :raise %))))))

(t/deftest stack-use-case
  (let [nested-ef    (fn [x]
                       (with-effects
                         (! (effect :prn "start nested-ef"))
                         (! (effect :prn x))
                         (! (effect :read))))
        ef           (fn [x]
                       (with-effects
                         (! (effect :prn "start ef"))
                         (! (nested-ef x))))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [handlers {:prn  (fn [_]  nil)
                      :read (fn [] "input string")}
            f        (fn [x]
                       (e/perform handlers continuation [x]))]
        (t/is (= "input string" (f  "some val")))))
    (t/testing "script"
      (let [script [{:args ["some val"]}
                    {:effect   [:prn "start ef"]
                     :coeffect nil}
                    {:effect   [:prn "start nested-ef"]
                     :coeffect nil}
                    {:effect   [:prn "some val"]
                     :coeffect nil}
                    {:effect   [:read]
                     :coeffect "input string"}
                    {:return "input string"}]]
        (script/test continuation script)))))

(t/deftest fallback
  (let [ef           (fn [x]
                       (with-effects
                         (let [a (! (effect :eff))
                               b (! [:not-effect])
                               c (! (inc x))]
                           [a b c])))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [handlers {:eff (fn [] :coeff)}
            f        (fn [x]
                       (e/perform handlers continuation [x]))]
        (t/is (= [:coeff [:not-effect] 1] (f 0)))))
    (t/testing "script"
      (let [script [{:args [0]}
                    {:effect   [:eff]
                     :coeffect :coeff}
                    {:return [:coeff [:not-effect] 1]}]]
        (script/test continuation script)))))

(t/deftest effect-as-value
  (let [effect-tag   :prn
        effect-arg   1
        test-effect  (effect effect-tag effect-arg)
        ef           (fn []
                       (with-effects
                         (! test-effect)))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:prn 1]
                       :coeffect nil}
                      {:return nil}]]
    (script/test continuation script)))

(t/deftest higher-order-effect
  (let [nested-ef    (fn []
                       (with-effects
                         (! (effect :a))
                         (effect :b)))
        ef           (fn []
                       (with-effects
                         (! (! (nested-ef)))))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:a]
                       :coeffect nil}
                      {:effect   [:b]
                       :coeffect :some-value}
                      {:return :some-value}]]
     (script/test continuation script)))

(t/deftest exceptions
  (t/testing "in ef"
    (let [ef           (fn []
                         (with-effects
                           (! (effect :prn "Throw!"))
                           (throw (ex-info "Test" {}))))
          continuation (e/continuation ef)
          handlers     {:prn (fn [_] nil)}
          f            (fn []
                         (e/perform handlers continuation []))]
      (t/is (thrown-with-msg? ExceptionInfo #"Test"
                              (f))))
    (t/testing "in handler"
      (let [ef           (fn []
                           (with-effects
                             (! (effect :prn "Throw!"))
                             :some-val))
            continuation (e/continuation ef)
            handlers     {:prn (fn [msg]
                                 (throw (ex-info "Test" {})))}
            f            (fn []
                           (e/perform handlers continuation []))]
        (t/is (thrown-with-msg? ExceptionInfo #"Test"
                                (f)))))
    (t/testing "catch in ef"
      (let [ef           (fn []
                           (with-effects
                             (try
                               (! (effect :prn "Throw!"))
                               (catch #?(:clj Throwable, :cljs js/Error) error
                                 :error))))
            continuation (e/continuation ef)
            handlers     {:prn (fn [msg]
                                 (throw (ex-info "Test" {})))}
            f            (fn []
                           (e/perform handlers continuation []))]
        (t/is (= :error (f)))))))

(t/deftest exceptions-in-ef-async
  (let [ef           (fn []
                       (with-effects
                         (! (effect :prn "Throw!"))
                         (throw (ex-info "Test" {}))))
        continuation (e/continuation ef)
        handlers     {:prn (fn [msg respond raise]
                             (next-tick respond nil))}
        f            (fn [respond raise]
                       (e/perform handlers continuation []
                                  respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :raise kind))
               (t/is (= "Test" (ex-message value)))
               (done))]
       (f #(check :respond %)
          #(check :raise %))))))

(t/deftest exceptions-in-handler-async
  (let [ef           (fn []
                       (with-effects
                         (! (effect :prn "Throw!"))
                         :some-val))
        continuation (e/continuation ef)
        handlers     {:prn (fn [msg respond raise]
                             (next-tick raise (ex-info "Test" {})))}
        f            (fn [respond raise]
                       (e/perform handlers continuation []
                                  respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :raise kind))
               (t/is (= "Test" (ex-message value)))
               (done))]
       (f #(check :respond %) #(check :raise %))))))

(t/deftest exceptions-catch-in-ef-async
  (let [ef           (fn []
                       (with-effects
                         (try
                           (! (effect :prn "Throw"))
                           (catch #?(:clj Throwable, :cljs js/Error) error
                             :error))))
        continuation (e/continuation ef)
        handlers     {:prn (fn [msg respond raise]
                             (next-tick raise (ex-info "Test" {})))}
        f            (fn [respond raise]
                       (e/perform handlers continuation []
                                  respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :respond kind))
               (t/is (= :error value))
               (done))]
       (f #(check :respond %) #(check :raise %))))))

(t/deftest multi-shot
  (let [ef                 (fn []
                             (with-effects
                               (! (effect :first))
                               (! (effect :second))
                               (! (effect :third))))
        continuation       (e/continuation ef)
        [eff continuation] (continuation [])]
    (t/is (= [:second]
             (first (continuation "first coeffect"))
             (first (continuation "first coeffect"))))))
