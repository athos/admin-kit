(ns admin-kit.transit
  (:require [cognitect.transit :as transit]))

(defrecord File [name content])

(defn file [name content-base64]
  (->File name content-base64))

(def write-handlers
  {File (transit/write-handler (constantly "admin-kit/file") #(into {} %))})

(def read-handlers
  {"admin-kit/file" (transit/read-handler map->File)})
