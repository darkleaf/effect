(ns darkleaf.effect.core
  (:require
   [darkleaf.generator.core :as gen]
   [darkleaf.generator.proto :as p]
   [darkleaf.effect.util :as u]))

(defrecord Effect [tag args])

#?(:clj (alter-meta! #'->Effect assoc :private true))
#?(:clj (alter-meta! #'map->Effect assoc :private true))

(defn effect [tag & args]
  (->Effect tag args))

(defn- wrap-pass-values [f*]
  (fn [& args]
    (let [gen        (apply f* args)
          pass-value #(when-not (p/-done? gen)
                        (let [v (p/-value gen)]
                          (when-not (instance? Effect v)
                            (p/-next gen v)
                            (recur))))]
      (pass-value)
      (reify
        p/Generator
        (-done? [_] (p/-done? gen))
        (-value [_] (p/-value gen))
        (-next [_ covalue]
          (p/-next gen covalue)
          (pass-value))
        (-throw [_ throwable]
          (p/-throw gen throwable)
          (pass-value))
        (-return [_ result]
          (p/-return gen result)
          (pass-value))))))

(defn wrap [f*]
  (-> f*
      gen/wrap-stack
      wrap-pass-values))

(defn perform
  ([handlers gen]
   (try
     (while (not (gen/done? gen))
       (let [{:keys [tag args]} (gen/value gen)
             handler            (u/getx handlers tag)
             [op covalue]       (try
                                  [gen/next (apply handler args)]
                                  (catch #?(:clj Exception :cljs js/Error) ex
                                    [gen/throw ex]))]
         (op gen covalue)))
     (gen/value gen)
     (catch #?(:clj Exception :cljs js/Error) ex
       (throw (ex-info "Error performing generator" {:effect (gen/value gen)} ex)))))
  ([handlers gen respond raise]
   (let [raise #(raise (ex-info "Error performing generator" {:effect (gen/value gen)} %))]
     (try
       (if (gen/done? gen)
         (respond (gen/value gen))
         (let [{:keys [tag args]} (gen/value gen)
               handler            (u/getx handlers tag)
               respond*           (fn [coeffect]
                                    (try
                                      (gen/next gen coeffect)
                                      (perform handlers gen respond raise)
                                      (catch #?(:clj Exception :cljs js/Error) ex
                                        (raise ex))))
               raise*             (fn [ex]
                                    (try
                                      (gen/throw gen ex)
                                      (perform handlers gen respond raise)
                                      (catch #?(:clj Exception :cljs js/Error) ex
                                        (raise ex))))]
           (apply handler (concat args [respond* raise*]))))
       (catch #?(:clj Exception :cljs js/Error) ex
         (raise ex))))))
