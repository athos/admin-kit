(ns admin-kit.adapter
  (:refer-clojure :exclude [read update count]))

(defprotocol Create
  (create [adapter params]))

(defprotocol Read
  (read [adapter params]))

(defprotocol Update
  (update [adapter params]))

(defprotocol Delete
  (delete [adapter params]))

(defprotocol Count
  (count [adapter params]))

(defn- unsupported [op]
  (fn [params]
    (let [msg (str (name op) " is not supported")]
      (throw (UnsupportedOperationException. msg)))))

(defprotocol ISupportedOps
  (supported-ops* [this]))

(defn make-adapter [{on-create :create, on-read :read, on-update :update
                     on-delete :delete, on-count :count :as ops}]
  (let [on-create (or on-create (unsupported :create))
        on-read (or on-read (unsupported :read))
        on-update (or on-update (unsupported :update))
        on-delete (or on-delete (unsupported :delete))
        on-count (or on-count (unsupported :count))
        supported-ops (->> [:create :read :update :delete :count]
                           (select-keys ops)
                           keys
                           set)]
    (reify
      Create
      (create [this params]
        (on-create params))
      Read
      (read [this params]
        (on-read params))
      Update
      (update [this params]
        (on-update params))
      Delete
      (delete [this params]
        (on-delete params))
      Count
      (count [this params]
        (on-count params))
      ISupportedOps
      (supported-ops* [this] supported-ops))))

(defn supported-ops [adapter]
  (if (satisfies? ISupportedOps adapter)
    (supported-ops* adapter)
    (->> {Create :create, Read :read, Update :update
          Delete :delete, Count :count}
         (reduce-kv (fn [ops proto op]
                      (if (satisfies? proto adapter)
                        (conj ops op)
                        ops))
                    #{}))))
