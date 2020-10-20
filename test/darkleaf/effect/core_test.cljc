(ns darkleaf.effect.core-test
  (:require
   [darkleaf.generator.core :as gen :refer [generator yield]]
   [darkleaf.effect.core :as e :refer [effect]]
   [clojure.test :as t])
  #?(:cljs (:require-macros [darkleaf.effect.core-test :refer [async]]))
  (:import
   #?(:clj [clojure.lang ExceptionInfo])))

(defmacro ^{:style/indent 1, :private true} async [done & body]
  (if (:js-globals &env) ;; if clojure-script?
    `(t/async ~done ~@body)
    `(let [done?# (atom false)
           ~done  (fn [] (reset! done?# true))]
       ~@body
       (assert @done?#))))

(defn- next-tick [f & args]
  #?(:clj  (apply f args)
     :cljs (apply js/setTimeout f 0 args)))

(t/deftest simple
  (let [f* (fn [k]
             (generator
               (int (* k (yield (effect :random))))))
        f* (e/fn-wrap f*)]
    (t/testing "interpretator"
      (let [handlers {:random (fn [] 0.1)}
            f (fn [k]
                (e/perform handlers (f* k)))]
        (t/is (= 1 (f 10)))))
    (t/testing "step by step"
      (let [gen (f* 10)]
        (t/is (= (effect :random) (gen/value gen)))
        (gen/next gen 0.1)
        (t/is (= 1 (gen/value gen)))
        (t/is (gen/done? gen))))))

(t/deftest simple-async
  (let [f*       (fn [k]
                   (generator
                     (int (* k (yield (effect :random))))))
        handlers {:random (fn [respond raise]
                            (next-tick respond 0.1))}
        f*       (e/fn-wrap f*)
        f        (fn [x respond raise]
                   (e/perform handlers (f* x) respond raise))]
    (async done
      (letfn [(check [kind value]
                (t/is (= :respond kind))
                (t/is (= 1 value))
                (done))]
        (f 10 #(check :respond %) #(check :raise %))))))

(t/deftest missed-handler
  (let [f*       (fn []
                   (generator
                     (yield (effect :random))))
        f*       (e/fn-wrap f*)
        handlers {}
        f        (fn []
                   (e/perform handlers (f*)))
        ex       (try (f) (catch ExceptionInfo ex ex))
        cause    (ex-cause ex)]
    (t/is (= "Error performing generator" (ex-message ex)))
    (t/is (= "Missing required key" (ex-message cause)))))

(t/deftest missed-handler-async
  (let [f*       (fn []
                   (generator
                     (yield (effect :random))))
        f*       (e/fn-wrap f*)
        handlers {}
        f        (fn [k respond raise]
                   (e/perform handlers (f*) respond raise))]
    (async done
      (letfn [(check [kind ex]
                (t/is (= :raise kind))
                (t/is (= "Error performing generator" (ex-message ex)))
                (t/is (= "Missing required key" (ex-message (ex-cause ex))))
                (done))]
        (f 10 #(check :respond %) #(check :raise %))))))

(t/deftest stack-use-case
  (let [nested-f* (fn [x]
                    (generator
                      (yield (effect :prn "start nested-ef"))
                      (yield (effect :prn x))
                      (yield (effect :read))))
        f*        (fn [x]
                    (generator
                      (yield (effect :prn "start ef"))
                      (yield (nested-f* x))))
        f*        (e/fn-wrap f*)]
    (t/testing "interpretator"
      (let [gen      (f* "some val")
            handlers {:prn  (fn [_]  nil)
                      :read (fn [] "input string")}]
        (t/is (= "input string" (e/perform handlers gen)))))
    (t/testing "step by step"
      (let [gen (f* "some val")]
        (t/is (= (effect :prn "start ef")
                 (gen/value gen)))
        (gen/next gen)

        (t/is (= (effect :prn "start nested-ef")
                 (gen/value gen)))
        (gen/next gen)

        (t/is (= (effect :prn "some val")
                 (gen/value gen)))
        (gen/next gen)

        (t/is (= (effect :read)
                 (gen/value gen)))
        (gen/next gen "input string")

        (t/is (= "input string" (gen/value gen)))
        (t/is (gen/done? gen))))))

(t/deftest fallback
  (let [f* (fn [x]
             (generator
               (let [a (yield (effect :eff))
                     b (yield [:not-effect])
                     c (yield (inc x))]
                 [a b c])))
        f* (e/fn-wrap f*)]
    (t/testing "interpretator"
      (let [gen      (f* 0)
            handlers {:eff (fn [] :coeff)}]
        (t/is (= [:coeff [:not-effect] 1] (e/perform handlers gen)))))
    (t/testing "script"
      (let [gen (f* 0)]
        (t/is (= (effect :eff)
                 (gen/value gen)))
        (gen/next gen :coeff)

        (t/is (= [:coeff [:not-effect] 1]
                 (gen/value gen)))
        (t/is (gen/done? gen))))))

(t/deftest effect-as-value
  (let [effect-tag  :prn
        effect-arg  1
        test-effect (effect effect-tag effect-arg)
        f*          (fn []
                      (generator
                        (yield test-effect)))
        f*          (e/fn-wrap f*)
        gen         (f*)]
    (t/is (= (effect :prn 1)
             (gen/value gen)))
    (gen/next gen)

    (t/is (nil? (gen/value gen)))
    (t/is (gen/done? gen))))

(t/deftest higher-order-effect
  (let [nested-f* (fn []
                    (generator
                      (yield (effect :a))
                      (effect :b)))
        f*        (fn []
                    (generator
                      (yield (yield (nested-f*)))))
        f*        (e/fn-wrap f*)
        gen (f*)]

    (t/is (= (effect :a)
             (gen/value gen)))
    (gen/next gen)

    (t/is (= (effect :b)
             (gen/value gen)))
    (gen/next gen :some-value)

    (t/is (= :some-value
             (gen/value gen)))
    (t/is (gen/done? gen))))

(t/deftest exception-in-f*
  (t/testing "in f*"
    (let [f*       (fn []
                     (generator
                       (yield (effect :prn "Throw!"))
                       (throw (ex-info "Test" {}))))
          f*       (e/fn-wrap f*)
          handlers {:prn (fn [_] nil)}
          f        (fn []
                     (e/perform handlers (f*)))
          ex       (try (f) (catch ExceptionInfo ex ex))
          cause    (ex-cause ex)]
      (t/is (= "Error performing generator" (ex-message ex)))
      (t/is (= {:effect (effect :prn "Throw!")}
               (ex-data ex)))
      (t/is (= "Test" (ex-message cause))))))

(t/deftest exception-in-f*-async
  (let [f*       (fn []
                   (generator
                     (yield (effect :prn "Throw!"))
                     (throw (ex-info "Test" {}))))
        f*       (e/fn-wrap f*)
        handlers {:prn (fn [msg respond raise]
                         (next-tick respond nil))}
        f        (fn [respond raise]
                   (e/perform handlers (f*) respond raise))]
    (async done
      (letfn [(check [kind ex]
                (t/is (= :raise kind))
                (t/is (= "Error performing generator" (ex-message ex)))
                (t/is (= {:effect (effect :prn "Throw!")}
                         (ex-data ex)))
                (t/is (= "Test" (ex-message (ex-cause ex))))
                (done))]
        (f #(check :respond %)
           #(check :raise %))))))

(t/deftest exeption-in-handler
  (let [f*       (fn []
                   (generator
                     (yield (effect :prn "Throw!"))
                     :some-val))
        f*       (e/fn-wrap f*)
        handlers {:prn (fn [msg]
                         (throw (ex-info "Test" {})))}
        f        (fn []
                   (e/perform handlers (f*)))
        ex       (try (f) (catch ExceptionInfo ex ex))
        cause    (ex-cause ex)]
      (t/is (= "Error performing generator" (ex-message ex)))
      (t/is (= {:effect (effect :prn "Throw!")}
               (ex-data ex)))
      (t/is (= "Test" (ex-message cause)))))

(t/deftest exception-in-handler-async
  (let [f*       (fn []
                   (generator
                     (yield (effect :prn "Throw!"))
                     :some-val))
        f*       (e/fn-wrap f*)
        handlers {:prn (fn [msg respond raise]
                         (next-tick raise (ex-info "Test" {})))}
        f        (fn [respond raise]
                   (e/perform handlers (f*) respond raise))]
    (async done
      (letfn [(check [kind ex]
                (t/is (= :raise kind))
                (t/is (= "Error performing generator" (ex-message ex)))
                (t/is (= {:effect (effect :prn "Throw!")}
                         (ex-data ex)))
                (t/is (= "Test" (ex-message (ex-cause ex))))
                (done))]
        (f #(check :respond %) #(check :raise %))))))

(t/deftest exception-catch-in-f*
  (let [f*       (fn []
                   (generator
                     (try
                       (yield (effect :prn "Throw!"))
                       (catch ExceptionInfo ex
                         (ex-message ex)))))
        f*       (e/fn-wrap f*)
        handlers {:prn (fn [msg]
                         (throw (ex-info "Test" {})))}
        f        (fn []
                   (e/perform handlers (f*)))]
    (t/is (= "Test" (f)))))

(t/deftest exception-catch-in-f*-async
  (let [f*       (fn []
                   (generator
                     (try
                       (yield (effect :prn "Throw"))
                       (catch ExceptionInfo ex
                         (ex-message ex)))))
        f*       (e/fn-wrap f*)
        handlers {:prn (fn [msg respond raise]
                         (next-tick raise (ex-info "Test" {})))}
        f        (fn [respond raise]
                   (e/perform handlers (f*) respond raise))]
    (async done
      (letfn [(check [kind value]
                (t/is (= :respond kind))
                (t/is (= "Test" value))
                (done))]
        (f #(check :respond %) #(check :raise %))))))
