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

(defn remove-fns [page-spec]
  (walk/prewalk
    (fn [x]
      (if (map? x)
        (reduce-kv #(if (fn? %3) %1 (assoc %1 %2 %3)) {} x)
        x))
    page-spec))

(defn field-formatters [page-spec]
  (reduce (fn [m field]
            (if-let [formatter (:format field)]
              (assoc m (:field field) formatter)
              m))
          {}
          (:fields page-spec)))

(defn default-formatter [x]
  (cond (number? x) x
        :else (str x)))

(defn format-item-fields [page-spec item]
  (let [formatters (field-formatters page-spec)]
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

(defn make-api-routes [page-name page-spec adapter]
  (routes
   (GET page-name {:keys [params]}
        (->> (adapter/read adapter params)
             (map #(format-item-fields page-spec %))
             res/response))
   (POST page-name {:keys [params]}
         (res/response (adapter/create adapter params)))
   (PUT (str page-name "/:id") {:keys [params]}
        (->> (adapter/update adapter params)
             (format-item-fields page-spec)
             res/response))
   (DELETE (str page-name "/:id") {:keys [params]}
           (res/response (adapter/delete adapter params)))
   (GET (str page-name "/_spec") []
        (res/response (remove-fns page-spec)))))

(defn make-apis-handler [site-spec]
  (->> (for [[page-name {page-spec :spec adapter :adapter}] site-spec
             :let [page-name (str "/" (name page-name))]]
         (make-api-routes page-name page-spec adapter))
       (apply routes)))

(defn render-page [page]
  (-> (io/file (io/resource (format "public/html/%s.html" page)))
      res/response
      (res/content-type "text/html")
      (res/charset "utf-8")))

(defn make-page-routes [page-name site-spec]
  (routes
   (GET page-name [] (render-page "page"))
   (GET (str page-name "/*") [] (render-page "page"))))

(defn make-pages-handler [site-spec]
  (->> (for [[page-name _] site-spec
             :let [page-name (str "/" (name page-name))]]
         (make-page-routes page-name site-spec))
       (apply routes)))

(defn make-admin-site-handler [site-spec]
  (routes
   (-> (context "/api" []
         (make-apis-handler site-spec))
       (wrap-restful-format :formats [:transit-json])
       (wrap-defaults api-defaults))
   (-> (routes
        (GET "/" [] (render-page "root"))
        (context "/pages" []
          (make-pages-handler site-spec))
        (route/resources "public"))
       (wrap-defaults site-defaults))))
