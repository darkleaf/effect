(ns darkleaf.effect.script
  (:refer-clojure :exclude [test])
  (:require
   [clojure.test :as t]
   [clojure.string :as str]))

(defprotocol EffectMatcher
  :extend-via-metadata true
  (effect-matches [matcher actual]))

(defprotocol ExceptionMatcher
  :extend-via-metadata true
  (exception-matches [matcher actual]))

(defn- with-exceptions [continuation]
  (when (some? continuation)
    (fn [coeffect]
      (try
        (let [[effect continuation] (continuation coeffect)
              continuation          (with-exceptions continuation)]
          [effect continuation])
        (catch #?(:clj RuntimeException, :cljs js/Error) ex
          [ex nil])))))

(defn- add-message-tag [message tag]
  (->> [tag message]
       (remove nil?)
       (str/join " / ")))

(defn- test-first-item [{:keys [report continuation]} {:keys [args]}]
  (let [[effect continuation] (continuation args)]
    {:report        report
     :actual-effect effect
     :continuation  continuation}))

(defn- next-step [{:keys [report continuation]} coeffect]
  (let [[actual-effect continuation] (continuation coeffect)]
    {:report        report
     :actual-effect actual-effect
     :continuation  continuation}))

(defn- test-middle-item [{:keys [report actual-effect continuation] :as ctx}
                         {:keys [effect coeffect tag]}]
  (cond
    (not= :pass (:type report))
    {:report report}

    (nil? continuation)
    {:report {:type     :fail
              :expected effect
              :actual   actual-effect
              :message  (add-message-tag "Misssed effect" tag)}}

    (and (some? effect)
         (not (effect-matches effect actual-effect)))
    {:report {:type     :fail
              :expected effect
              :actual   actual-effect
              :message  (add-message-tag "Wrong effect" tag)}}

    :else
    (next-step ctx coeffect)))

(defn- test-middle-items [ctx items]
  (reduce test-middle-item ctx items))

(defn- test-last-item [{:keys [report actual-effect continuation]}
                       {:keys [return final-effect thrown tag]}]
  (cond
    (not= :pass (:type report))
    {:report report}

    (and (some? final-effect)
         (effect-matches final-effect actual-effect))
    {:report report}

    (some? final-effect)
    {:report {:type     :fail
              :expected final-effect
              :actual   actual-effect
              :message  (add-message-tag "Wrong final effect" tag)}}

    (and (some? thrown)
         (exception-matches thrown actual-effect))
    {:report report}

    (some? thrown)
    {:report {:type     :fail
              :expected thrown
              :actual   actual-effect
              :message  (add-message-tag "Wrong exception" tag)}}

    (some? continuation)
    {:report {:type     :fail
              :expected nil
              :actual   actual-effect
              :message  (add-message-tag "Extra effect" tag)}}

    (effect-matches return actual-effect)
    {:report report}

    :else
    {:report {:type     :fail
              :expected return
              :actual   actual-effect
              :message  (add-message-tag "Wrong return" tag)}}))

(defn test* [continuation script]
  {:pre [(<= 2 (count script))]}
  (let [first-item   (first script)
        middle-items (-> script rest butlast)
        last-item    (last script)
        continuation (-> continuation
                         (with-exceptions))]
    (-> {:continuation continuation, :report {:type :pass}}
        (test-first-item first-item)
        (test-middle-items middle-items)
        (test-last-item last-item)
        :report)))

(defn test [continuation script]
  (-> (test* continuation script)
      (t/do-report)))

(extend-protocol EffectMatcher
  nil
  (effect-matches [_ effect]
    (nil? effect))

  #?(:clj Object :cljs default)
  (effect-matches [matcher effect]
    (= matcher effect))

  #?(:clj clojure.lang.Fn :cljs function)
  (effect-matches [matcher effect]
    (matcher effect)))

(extend-protocol ExceptionMatcher
  #?(:clj Throwable, :cljs default)
  (exception-matches [a b]
    (and (= (type a)
            (type b))
         (= (ex-message a)
            (ex-message b))
         (= (ex-data a)
            (ex-data b))))

  #?(:clj clojure.lang.Fn :cljs function)
  (exception-matches [matcher exception]
    (matcher exception)))
