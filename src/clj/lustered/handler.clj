(ns lustered.handler
  (:require [mount.core :refer [defstate]]
            [net.cgrand.enlive-html :as enlive]
            [ring.util.response :as res]
            [compojure
             [core :refer :all]
             [route :as route]]
            [ring.middleware
             [defaults :refer [wrap-defaults site-defaults api-defaults]]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [clojure.java.io :as io]))

(defstate page-template
  :start (enlive/template (io/resource "public/index.html") []))

(defn render-root-page []
  (-> (res/response (apply str (page-template)))
      (res/content-type "text/html")
      (res/charset "utf-8")))

(defn make-site-routes [page-name spec]
  (-> (routes
       (GET page-name [] (render-root-page))
       (GET (str page-name "/*") [] (render-root-page))
       (route/resources "public"))
      (wrap-defaults site-defaults)))

(defn make-api-routes [page-name spec]
  (let [{:keys [on-create on-read on-update on-delete]} spec]
    (-> (routes
         (GET page-name {:keys [params]}
           (res/response (on-read params)))
         (POST page-name {:keys [params]}
           (res/response (on-create params)))
         (PUT (str page-name "/:id") {:keys [params]}
           (res/response (on-update params)))
         (DELETE (str page-name "/:id") {:keys [params]}
           (res/response (on-delete params))))
        (wrap-restful-format :formats [:transit-json])
        (wrap-defaults api-defaults))))

(defn make-admin-page-handler [root-path {page-name :name :as spec}]
  (let [page-name (str "/" (name page-name))]
    (context root-path []
      (context "/api" []
        (make-api-routes page-name spec))
      (make-site-routes page-name spec))))
