(ns lustered.adapter
  (:refer-clojure :exclude [read update]))

(defprotocol Create
  (create [adapter params]))

(defprotocol Read
  (read [adapter params]))

(defprotocol Update
  (update [adapter params]))

(defprotocol Delete
  (delete [adapter params]))

(defn make-adapter [{:keys [create read update delete]}]
  (let [unsupported (fn [op]
                      (fn [params]
                        (let [msg (str (name op) " is not supported")]
                          (throw (UnsupportedOperationException. msg)))))
        on-create (or create (unsupported :create))
        on-read (or read (unsupported :read))
        on-update (or update (unsupported :update))
        on-delete (or delete (unsupported :delete))]
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
        (on-delete params)))))
