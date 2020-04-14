(ns darkleaf.effect.script
  (:refer-clojure :exclude [test])
  (:require
   [clojure.test :as t]
   [clojure.string :as str]
   [clojure.data :as data]
   [darkleaf.effect.internal :as i]))

(defprotocol Matcher
  :extend-via-metadata true
  (matcher-report [matcher actual]))

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
                         {:keys [effect coeffect tag] :as item}]
  (i/<<-
   (if (not= :pass (:type report))
     {:report report})

   (if (nil? continuation)
     {:report {:type     :fail
               :expected effect
               :actual   actual-effect
               :message  (add-message-tag "Missed effect" tag)}})

   (if (contains? item :effect)
     (if-some [report (matcher-report effect actual-effect)]
       {:report (assoc report
                       :type :fail
                       :message (add-message-tag "Wrong effect" tag))}
       (next-step ctx coeffect)))

   {:report {:type     :fail
             :expected '(contains? script-item :effect)
             :actual   item
             :message  (add-message-tag "Wrong script item" tag)}}))

(defn- test-middle-items [ctx items]
  (reduce test-middle-item ctx items))

(defn- test-last-item [{:keys [report actual-effect continuation]}
                       {:keys [return final-effect thrown tag] :as item}]
  (i/<<-
   (if (not= :pass (:type report))
     {:report report})

   (if (contains? item :final-effect)
     (if-some [report (matcher-report final-effect actual-effect)]
       {:report (assoc report
                       :type :fail
                       :message (add-message-tag "Wrong final effect" tag))}
       {:report report}))

   (if (contains? item :thrown)
     (if-some [report (matcher-report thrown actual-effect)]
       {:report (assoc report
                       :type :fail
                       :message (add-message-tag "Wrong exception" tag))}
       {:report report}))

   (if (some? continuation)
     {:report {:type     :fail
               :expected nil
               :actual   actual-effect
               :message  (add-message-tag "Extra effect" tag)}})

   (if (contains? item :return)
     (if-some [report (matcher-report return actual-effect)]
       {:report (assoc report
                       :type :fail
                       :message (add-message-tag "Wrong return" tag))}
       {:report report}))

   {:report {:type     :fail
             :expected '(or (contains? script-item :return)
                            (contains? script-item :final-effect)
                            (contains? script-item :thrown))
             :actual   item
             :message  (add-message-tag "Wrong script item" tag)}}))

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

(defn- ex->data [ex]
  {:type    (type ex)
   :message (ex-message ex)
   :data    (ex-data ex)})

(extend-protocol Matcher
  nil
  (matcher-report [_ actual]
    (if-not (nil? actual)
      {:expected nil
       :actual actual}))

  #?(:clj Object :cljs default)
  (matcher-report [matcher actual]
    (when (not= matcher actual)
      {:expected matcher
       :actual   actual
       :diffs    [[actual (data/diff matcher actual)]]}))

  #?(:clj Throwable, :cljs js/Error)
  (matcher-report [matcher actual]
    (i/<<-
     (if-not (i/exception? actual)
       {:expected matcher
        :actual   actual})
     (let [matcher-data (ex->data matcher)
           actual-data  (ex->data actual)])
     (if-not (= matcher-data actual-data)
       {:expected matcher
        :actual   actual
        :diffs    [[actual (data/diff matcher-data actual-data)]]}))))
