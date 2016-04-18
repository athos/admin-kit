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

(defprotocol Validate
  (validate [adapter params]))

(defn- unsupported [op]
  (let [msg (str (name op) " is not supported")]
    (throw (UnsupportedOperationException. msg))))

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
                     on-delete :delete, on-count :count, on-validate :validate}]
  (let [adapter (reify Read
                  (read [this params]
                    (on-read params)))]
    (when on-create
      (extend-type (class adapter)
        Create
        (create [this params]
          (on-create params))))
    (when on-update
      (extend-type (class adapter)
        Update
        (update [this params]
          (on-update params))))
    (when on-delete
      (extend-type (class adapter)
        Delete
        (delete [this params]
          (on-delete params))))
    (when on-count
      (extend-type (class adapter)
        Count
        (count [this params]
          (on-count params))))
    (when on-validate
      (extend-type (class adapter)
        Validate
        (validate [this params]
          (on-validate params))))))
