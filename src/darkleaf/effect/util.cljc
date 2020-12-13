(ns darkleaf.effect.util)

(defn getx
  "Like two-argument get, but throws an exception if the key is not found."
  ([m k]
   (getx m k "Missing required key"))
  ([m k msg]
   (let [e (get m k ::sentinel)]
     (if-not (= e ::sentinel)
       e
       (throw (ex-info msg  {:map m :key k}))))))

(defn getx-in
  "Like two-argument get-in, but throws an exception if the keys are not found."
  ([m ks]
   (getx-in m ks "Missing required keys"))
  ([m ks msg]
   (let [e (get-in m ks ::sentinel)]
     (if-not (= e ::sentinel)
       e
       (throw (ex-info msg {:map m :keys ks}))))))
