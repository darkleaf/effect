(ns darkleaf.effect.core
  (:require
   [darkleaf.generator.core :as gen]
   [darkleaf.effect.impl :as impl]
   [darkleaf.effect.util :as u]))

(defn effect [tag & args]
  (impl/->Effect tag args))

(defn wrap [f*]
  (-> f*
      gen/wrap-stack
      impl/wrap-pass-values))

;; я тут везде js/Error делаю, а может нужно :default, из-за gen/return
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
