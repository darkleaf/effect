(ns darkleaf.effect.util)

(defn getx
  "Like two-argument get, but throws an exception if the key is
   not found."
  [m k]
  (let [e (get m k ::sentinel)]
    (if-not (= e ::sentinel)
      e
      (throw (ex-info "Missing required key" {:map m :key k})))))

(defn getx-in
  "Like two-argument get-in, but throws an exception if the key is
   not found."
  [m ks]
  (reduce getx m ks))
