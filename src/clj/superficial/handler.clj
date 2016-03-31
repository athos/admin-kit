(ns superficial.handler
  (:require [net.cgrand.enlive-html :refer [deftemplate]]
            [bidi
             [bidi :as bidi]
             [ring :as ring]]
            [ring.util.response :as res]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.java.io :as io]))

(deftemplate page (io/resource "public/index.html") [])

(defn root-page-handler [req]
  (res/response (apply str (page))))

(defn make-crud-handlers [spec]
  (let [{:keys [on-create on-read on-update on-delete]} spec]
    {:get {"" (fn [req] (res/response "read"))}
     :post {"" (fn [req] (res/response "create"))}
     :put {["/" :id] (fn [req] (res/response "update"))}
     :delete {["/" :id] (fn [req] (res/response "delete"))}}))

(defn make-routes [{page-name :name :as spec}]
  (let [page-name (name page-name)]
    ["/" {page-name (-> root-page-handler
                         (wrap-defaults site-defaults))
          ["api/" page-name] (make-crud-handlers spec)}]))

(defn make-admin-page-handler [spec]
  (let [routes (make-routes spec)]
    (ring/make-handler routes)))
