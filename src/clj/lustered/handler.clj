(ns lustered.handler
  (:require [mount.core :refer [defstate]]
            [ring.util.response :as res]
            [compojure
             [core :refer :all]
             [route :as route]]
            [ring.middleware
             [defaults :refer [wrap-defaults site-defaults api-defaults]]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [lustered.adapter :as adapter]))

(defn ->str [x]
  (if (keyword? x)
    (name x)
    (str x)))

(defn render-root-page []
  (-> (res/response (io/file (io/resource "public/index.html")))
      (res/content-type "text/html")
      (res/charset "utf-8")))

(defn make-site-routes [page-name spec]
  (-> (routes
       (GET page-name [] (render-root-page))
       (GET (str page-name "/*") [] (render-root-page))
       (route/resources "public"))
      (wrap-defaults site-defaults)))

(defn remove-fns [spec]
  (walk/prewalk
    (fn [x]
      (if (map? x)
        (reduce-kv #(if (fn? %3) %1 (assoc %1 %2 %3)) {} x)
        x))
    spec))

(defn field-formatters [spec]
  (reduce (fn [m field]
            (if-let [formatter (:format field)]
              (assoc m (:field field) formatter)
              m))
          {}
          (:fields spec)))

(defn default-formatter [x]
  (cond (number? x) x
        :else (str x)))

(defn format-item-fields [spec item]
  (let [formatters (field-formatters spec)]
    (reduce-kv (fn [item field-name field-value]
                 (let [formatter (or (get formatters field-name)
                                     default-formatter)
                       formatted (formatter field-value)]
                   (if (not= field-value formatted)
                     (assoc item
                            (keyword "_formatted" (->str field-name))
                            formatted)
                     item)))
               item
               item)))

(defn make-api-routes [page-name spec adapter]
  (-> (routes
       (GET page-name {:keys [params]}
         (->> (adapter/read adapter params)
              (map #(format-item-fields spec %))
              res/response))
       (POST page-name {:keys [params]}
         (res/response (adapter/create adapter params)))
       (PUT (str page-name "/:id") {:keys [params]}
         (->> (adapter/update adapter params)
              (format-item-fields spec)
              res/response))
       (DELETE (str page-name "/:id") {:keys [params]}
         (res/response (adapter/delete adapter params)))
       (GET (str page-name "/_spec") []
         (res/response (remove-fns spec))))
      (wrap-restful-format :formats [:transit-json])
      (wrap-defaults api-defaults)))

(defn make-admin-page-handler [root-path page-name page-spec adapter]
  (let [page-name (str "/" (name page-name))]
    (context root-path []
      (context "/_api" []
        (make-api-routes page-name page-spec adapter))
      (make-site-routes page-name page-spec))))

(defn make-admin-site-handler [root-path site-spec]
  (->> (for [[page-name {page-spec :spec adapter :adapter}] site-spec]
         (make-admin-page-handler root-path page-name page-spec adapter))
       (apply routes)))
