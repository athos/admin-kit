(ns superficial.handler
  (:require [ring.util.response :as res]))

(defn make-admin-page-handler [spec]
  (fn [req]
    (res/response "Welcome to admin page!!")))
