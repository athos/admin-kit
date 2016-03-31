(ns dev
  (:require [clojure.repl :refer :all]
            [clojure.pprint :refer [pp pprint]]
            [mount.core :as m]
            [clojure.tools.namespace.repl :refer [refresh]]
            [lustered.handler :as lustered]))

(defn go []
  (m/start))

(defn stop []
  (m/stop))

(defn reset []
  (stop)
  (refresh :after 'dev/go))
