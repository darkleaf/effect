(ns darkleaf.effect.core-test
  (:require
   [darkleaf.effect.core :as e :refer [with-effects ! effect]]
   [darkleaf.effect.script :as script]
   #?(:clj  [clojure.core.match :refer [match]]
      :cljs [cljs.core.match :refer-macros [match]])
   [clojure.test :as t]))

(defn- next-tick [f & args]
  #?(:clj  (apply f args)
     :cljs (apply js/setTimeout f 0 args)))

(t/deftest simple
  (let [ef           (fn [x]
                       (with-effects
                         (let [rnd (! (effect [:random]))]
                           (- (* 2. x rnd)
                              x))))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:random] 0.75))
            f                 (fn [x]
                                (e/perform effect-!>coeffect continuation [x]))]
        (t/is (= 0.5 (f 1)))))
    (t/testing "script"
      (let [script [{:args [1]}
                    {:effect   [:random]
                     :coeffect 0.75}
                    {:return 0.5}]]
        (script/test continuation script)))))

(t/deftest simple-async
  (let [ef                (fn [x]
                            (with-effects
                              (let [rnd (! (effect [:random]))]
                                (- (* 2. x rnd)
                                   x))))
        effect-!>coeffect (fn [effect respond raise]
                            (match effect
                                   [:random] (next-tick respond 0.75)))
        continuation      (e/continuation ef)
        f                 (fn [x respond raise]
                            (e/perform effect-!>coeffect
                                       continuation
                                       [x]
                                       respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :respond kind))
               (t/is (= 0.5 value))
               (done))]
       (f 1 #(check :respond %) #(check :raise %))))))

(t/deftest stack-use-case
  (let [nested-ef    (fn [x]
                       (with-effects
                         (! (effect [:prn "start nested-ef"]))
                         (! (effect [:prn x]))
                         (! (effect [:read]))))
        ef           (fn [x]
                       (with-effects
                         (! (effect [:prn "start ef"]))
                         (! (nested-ef x))))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:prn _]  nil
                                       [:read]   "input string"))
            f                 (fn [x]
                                (e/perform effect-!>coeffect continuation [x]))]
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

(t/deftest types-of-effects
  (let [ef           (fn []
                       (with-effects
                         [(! (effect [:effect-1 :val-1]))
                          (! (effect {:type :effect-2
                                      :arg  :val-2}))
                          (! (effect 'effect-3))]))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:effect-1 arg] arg
                                       {:type :effect-2, :arg arg} arg
                                       'effect-3 :val-3))
            f                 (fn []
                                (e/perform effect-!>coeffect continuation []))]
        (t/is (= [:val-1 :val-2 :val-3] (f)))))
    (t/testing "script"
      (let [script [{:args []}
                    {:effect   [:effect-1 :val-1]
                     :coeffect :val-1}
                    {:effect   {:type :effect-2
                                :arg  :val-2}
                     :coeffect :val-2}
                    {:effect   'effect-3
                     :coeffect :val-3}
                    {:return [:val-1 :val-2 :val-3]}]]
        (script/test continuation script)))))

(t/deftest fallback
  (let [ef           (fn [x]
                       (with-effects
                         (let [a (! (effect [:eff]))
                               b (! [:not-effect])
                               c (! (inc x))]
                           [a b c])))
        continuation (e/continuation ef)]
    (t/testing "interpretator"
      (let [effect-!>coeffect (fn [effect]
                                (match effect
                                       [:eff] :coeff))
            f                 (fn [x]
                                (e/perform effect-!>coeffect continuation [x]))]
        (t/is (= [:coeff [:not-effect] 1] (f 0)))))
    (t/testing "script"
      (let [script [{:args [0]}
                    {:effect   [:eff]
                     :coeffect :coeff}
                    {:return [:coeff [:not-effect] 1]}]]
        (script/test continuation script)))))

(t/deftest effect-as-value
  (let [effect-data  [:prn 1]
        test-effect  (effect effect-data)
        ef           (fn []
                       (with-effects
                         (! test-effect)))
        continuation (e/continuation ef)
        script       [{:args []}
                      {:effect   [:prn 1]
                       :coeffect nil}
                      {:return nil}]]
    (script/test continuation script)))

(t/deftest exceptions
  (t/testing "in ef"
    (let [ef                (fn []
                              (with-effects
                                (! (effect [:prn "Throw!"]))
                                (throw (ex-info "Test" {}))))
          continuation      (e/continuation ef)
          effect-!>coeffect (fn [effect]
                              (match effect
                                     [:prn msg] nil))
          f                 (fn []
                              (e/perform effect-!>coeffect continuation []))]
      (t/is (thrown-with-msg? #?(:clj  clojure.lang.ExceptionInfo
                                 :cljs cljs.core.ExceptionInfo)
                              #"Test"
                              (f))))
    (t/testing "in effect-!>coeffect"
      (let [ef                (fn []
                                (with-effects
                                  (! (effect [:prn "Throw!"]))
                                  :some-val))
            continuation      (e/continuation ef)
            effect-!>coeffect (fn [effect]
                                (throw (ex-info "Test" {})))
            f                 (fn []
                                (e/perform effect-!>coeffect continuation []))]
        (t/is (thrown-with-msg? #?(:clj  clojure.lang.ExceptionInfo
                                   :cljs cljs.core.ExceptionInfo)
                                #"Test"
                                (f)))))
    (t/testing "catch in ef"
      (let [ef                (fn []
                                (with-effects
                                  (try
                                    (! (effect [:prn "Throw!"]))
                                    (catch #?(:clj Throwable, :cljs js/Error) error
                                      :error))))
            continuation      (e/continuation ef)
            effect-!>coeffect (fn [effect]
                                (throw (ex-info "Test" {})))
            f                 (fn []
                                (e/perform effect-!>coeffect continuation []))]
        (t/is (= :error (f)))))))

(t/deftest exceptions-in-ef-async
  (let [ef                (fn []
                            (with-effects
                              (! (effect [:prn "Throw!"]))
                              (throw (ex-info "Test" {}))))
        continuation      (e/continuation ef)
        effect-!>coeffect (fn [effect respond raise]
                            (match effect
                                   [:prn msg] (next-tick respond nil)))
        f                 (fn [respond raise]
                            (e/perform effect-!>coeffect continuation []
                                       respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :raise kind))
               (t/is (= "Test" (ex-message value)))
               (done))]
       (f #(check :respond %)
          #(check :raise %))))))

(t/deftest exceptions-in-effect-!>coeffect-async
  (let [ef                (fn []
                            (with-effects
                              (! (effect [:prn "Throw!"]))
                              :some-val))
        continuation      (e/continuation ef)
        effect-!>coeffect (fn [effect respond raise]
                            (next-tick raise (ex-info "Test" {})))
        f                 (fn [respond raise]
                            (e/perform effect-!>coeffect continuation []
                                       respond raise))]
    (#?@(:cljs [t/async done], :clj [let [done (fn [])]])
     (letfn [(check [kind value]
               (t/is (= :raise kind))
               (t/is (= "Test" (ex-message value)))
               (done))]
       (f #(check :respond %) #(check :raise %))))))

(t/deftest exceptions-catch-in-ef-async
  (let [ef                (fn []
                            (with-effects
                              (try
                                (! (effect [:prn "Throw"]))
                                (catch #?(:clj Throwable, :cljs js/Error) error
                                  :error))))
        continuation      (e/continuation ef)
        effect-!>coeffect (fn [effect respond raise]
                            (next-tick raise (ex-info "Test" {})))
        f                 (fn [respond raise]
                            (e/perform effect-!>coeffect continuation []
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
                               (! (effect [:first]))
                               (! (effect [:second]))
                               (! (effect [:third]))))
        continuation       (e/continuation ef)
        [eff continuation] (continuation [])]
    (t/is (= (first (continuation "first coeffect"))
             (first (continuation "first coeffect"))))))
