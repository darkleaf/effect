(ns darkleaf.effect.script-test
  (:require
   [darkleaf.effect.core :as e :refer [break ! effect]]
   [darkleaf.effect.script :as script]
   [clojure.test :as t]))

(t/deftest script
    (let [ef           (fn [x]
                         (break
                           (! (effect [:some-eff x]))))
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
                    :message  "Wrong return"}
                   (script/test* continuation script)))))
      (t/testing "wrong final-effect"
        (let [script [{:args [:value]}
                      {:final-effect [:wrong]}]]
          (t/is (= {:type     :fail,
                    :expected [:wrong],
                    :actual   [:some-eff :value],
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
                    :actual   nil
                    :message  "Misssed effect"}
                   (script/test* continuation script)))))))

(t/deftest trivial-script
    (let [ef           (fn [x]
                         (break
                           x))
          continuation (e/continuation ef)
          script       [{:args [:value]}
                        {:return :value}]]
      (script/test continuation script)))
