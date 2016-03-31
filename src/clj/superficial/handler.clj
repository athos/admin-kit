(ns superficial.handler
  (:require [mount.core :refer [defstate]]
            [net.cgrand.enlive-html :as enlive]
            [bidi
             [bidi :as bidi]
             [ring :as ring]]
            [ring.util.response :as res]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clojure.java.io :as io]))

(defstate page-template
  :start (enlive/template (io/resource "public/index.html") []))

(defn root-page-handler [req]
  (-> (res/response (apply str (page-template)))
      (res/content-type "text/html")
      (res/charset "utf-8")))

(defn make-crud-handlers [spec]
  (let [{:keys [on-create on-read on-update on-delete]} spec]
    {:get {"" (fn [req] (res/response "read"))}
     :post {"" (fn [req] (res/response "create"))}
     :put {["/" :id] (fn [req] (res/response "update"))}
     :delete {["/" :id] (fn [req] (res/response "delete"))}}))

(defn make-routes [^String path {page-name :name :as spec}]
  (let [path (if (.endsWith path "/") path (str path "/"))
        page-name (name page-name)]
    [path {page-name {true (-> root-page-handler
                               (wrap-defaults site-defaults))}
           ["api/" page-name] (make-crud-handlers spec)}]))

(defn make-admin-page-handler [path spec]
  (let [routes (make-routes path spec)]
    (ring/make-handler routes)))
