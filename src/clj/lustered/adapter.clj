(ns lustered.adapter
  (:refer-clojure :exclude [read update]))

(ns-unmap *ns* 'Readable)

(defprotocol Creatable
  (create [adapter params]))

(defprotocol Readable
  (read [adapter params]))

(defprotocol Updatable
  (update [adapter params]))

(defprotocol Deletable
  (delete [adapter params]))
