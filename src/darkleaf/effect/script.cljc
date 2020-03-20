(ns darkleaf.effect.script
  (:refer-clojure :exclude [test])
  (:require
   [clojure.test :as t]
   [clojure.string :as str]))

(defn- with-exceptions [continuation]
  (when (some? continuation)
    (fn [coeffect]
      (try
        (let [[effect continuation] (continuation coeffect)
              continuation          (with-exceptions continuation)]
          [effect continuation])
        (catch #?(:clj RuntimeException, :cljs js/Error) ex
          [ex nil])))))

(defn- exception? [x]
  (instance? #?(:clj Throwable, :cljs js/Error) x))

(defn- equal-exceptions? [a b]
  (and (= (type a)
          (type b))
       (= (ex-message a)
          (ex-message b))
       (= (ex-data a)
          (ex-data b))))

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

    (= effect actual-effect)
    (next-step ctx coeffect)

    (and (fn? effect)
         (effect actual-effect))
    (next-step ctx coeffect)

    :else
    {:report {:type     :fail
              :expected effect
              :actual   actual-effect
              :message  (add-message-tag "Wrong effect" tag)}}))

(defn- test-middle-items [ctx items]
  (reduce test-middle-item ctx items))

(defn- test-last-item [{:keys [report actual-effect continuation]}
                       {:keys [return final-effect throw tag]}]
  (cond
    (not= :pass (:type report))
    {:report report}

    (and (some? final-effect)
         (= final-effect actual-effect))
    {:report report}

    (and (fn? final-effect)
         (final-effect actual-effect))
    {:report report}

    (some? final-effect)
    {:report {:type     :fail
              :expected final-effect
              :actual   actual-effect
              :message  (add-message-tag "Wrong final effect" tag)}}

    (and (some? throw)
         (equal-exceptions? throw actual-effect))
    {:report report}

    (and (fn? throw)
         (apply throw [actual-effect])) ;;fix me
    {:report report}

    (some? throw)
    {:report {:type     :fail
              :expected throw
              :actual   actual-effect
              :message  (add-message-tag "Wrong exception" tag)}}

    (some? continuation)
    {:report {:type     :fail
              :expected nil
              :actual   actual-effect
              :message  (add-message-tag "Extra effect" tag)}}

    (= return actual-effect)
    {:report report}

    (and (fn? return)
         (return actual-effect))
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
