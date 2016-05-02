(ns lustered.adapter
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

(extend-type Object
  Create
  (create [this params]
    (unsupported :create))
  Read
  (read [this params]
    (unsupported :read))
  Update
  (update [this params]
    (unsupported :update))
  Delete
  (delete [this params]
    (unsupported :delete)))

(defn make-adapter [{on-create :create, on-read :read, on-update :update
                     on-delete :delete, on-count :count :as ops}]
  (let [on-create (or on-create (unsupported :create))
        on-read (or on-read (unsupported :read))
        on-update (or on-update (unsupported :update))
        on-delete (or on-delete (unsupported :delete))
        on-count (or on-count (unsupported :count))]
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
        (on-count params)))))
