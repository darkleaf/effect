(ns darkleaf.effect.script-test
  (:require
   [darkleaf.effect.core :as e :refer [with-effects ! effect]]
   [darkleaf.effect.script :as script]
   [clojure.test :as t])
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(t/deftest script
  (let [ef           (fn [x]
                       (with-effects
                         (! (effect :some-eff x))))
        continuation (e/continuation ef)]
    (t/testing "correct"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:return :other-value}]]
        (script/test continuation script)))
    (t/testing "final-effect"
      (let [script [{:args [:value]}
                    {:final-effect [:some-eff :value]}]]
        (script/test continuation script)))
    (t/testing "wrong effect"
      (let [script [{:args [:value]}
                    {:effect   [:wrong]
                     :coeffect :other-value}
                    {:return :other-value}]]
        (t/is (= {:type     :fail
                  :expected [:wrong]
                  :actual   [:some-eff :value]
                  :diffs    [[[:some-eff :value]
                              [[:wrong] [:some-eff :value] nil]]],
                  :message  "Wrong effect"}
                 (script/test* continuation script)))))
    (t/testing "wrong return"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:return :wrong}]]
        (t/is (= {:type     :fail
                  :expected :wrong
                  :actual   :other-value
                  :diffs    [[:other-value
                              [:wrong :other-value nil]]]
                  :message  "Wrong return"}
                 (script/test* continuation script)))))
    (t/testing "wrong final-effect"
      (let [script [{:args [:value]}
                    {:final-effect [:wrong]}]]
        (t/is (= {:type     :fail
                  :expected [:wrong]
                  :actual   [:some-eff :value]
                  :diffs    [[[:some-eff :value]
                              [[:wrong] [:some-eff :value] nil]]]
                  :message  "Wrong final effect"}
                 (script/test* continuation script)))))
    (t/testing "extra effect"
      (let [script [{:args [:value]}
                    {:return :wrong}]]
        (t/is (=  {:type     :fail
                   :expected nil
                   :actual   [:some-eff :value]
                   :message  "Extra effect"}
                  (script/test* continuation script)))))
    (t/testing "missed effect"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:effect   [:extra-eff :value]
                     :coeffect :some-value}
                    {:return :some-other-value}]]
        (t/is (= {:type     :fail
                  :expected [:extra-eff :value]
                  :actual   :other-value
                  :message  "Unexpected return. An effect is expected."}
                 (script/test* continuation script)))))
    (t/testing "final-effect instead return"
      (let [script [{:args [:value]}
                    {:effect   [:some-eff :value]
                     :coeffect :other-value}
                    {:final-effect :other-value}]]
        (t/is (= {:type     :fail
                  :expected '(some? continuation)
                  :actual   :other-value
                  :message  "A value was unexpectedly returned"}
                 (script/test* continuation script)))))))

(t/deftest trivial-script
  (let [ef           (fn [x]
                       (with-effects
                         x))
        continuation (e/continuation ef)
        script       [{:args [:value]}
                      {:return :value}]]
    (script/test continuation script)))

(t/deftest exception
  (let [ef           (fn []
                       (with-effects
                         (! (effect :some-eff))
                         (throw (ex-info "Message" {:foo :bar}))))
        continuation (e/continuation ef)]
    (t/testing "correct"
      (let [script [{:args []}
                    {:effect   [:some-eff]
                     :coeffect :some-coeff}
                    {:thrown {:type    ExceptionInfo
                              :message "Message"
                              :data    {:foo :bar}}}]]
        (script/test continuation script)))
    (t/testing "unexpected exception"
      (let [script [{:args []}
                    {:effect   [:some-eff]
                     :coeffect :some-coeff}
                    {:return :ok}]
            report (script/test* continuation script)]
        (t/is (= :error (:type report)))))
    (t/testing "wrong exception type"
      (let [script [{:args []}
                    {:effect   [:some-eff]
                     :coeffect :some-coeff}
                    {:thrown #?(:clj  (RuntimeException. "Some msg")
                                :cljs (js/Error. "Some msg"))}]
            report (script/test* continuation script)]
        (t/is (= :fail (:type report)))
        (t/is (= "Wrong exception" (:message report)))))
    (t/testing "wrong exception message"
      (let [script [{:args []}
                    {:effect   [:some-eff]
                     :coeffect :some-coeff}
                    {:thrown (ex-info "Wrong message" {:foo :bar})}]
            report (script/test* continuation script)]
        (t/is (= :fail (:type report)))
        (t/is (= "Wrong exception" (:message report)))))
    (t/testing "wrong exception data"
      (let [script [{:args []}
                    {:effect   [:some-eff]
                     :coeffect :some-coeff}
                    {:thrown (ex-info "Message" {:foo :wrong})}]
            report (script/test* continuation script)]
        (t/is (= :fail (:type report)))
        (t/is (= "Wrong exception" (:message report)))))))
