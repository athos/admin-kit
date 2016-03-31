(ns example.core
  (:require [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [ring.util.response :refer [response]]
            [ring.adapter.jetty :refer [run-jetty]]))

(defn handler [req]
  (response "Hello, World!!"))

(defn start-server []
  (let [port (Long/parseLong (get env :port "8080"))]
    (run-jetty handler {:port port :join? false})))

(defstate server
  :start (start-server)
  :stop (.stop server))
