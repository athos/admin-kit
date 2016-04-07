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

(defn default-formatter [x]
  (cond (number? x) x
        :else (str x)))

(defn ->renderer [field-name formatter]
  (fn [item]
    (formatter (get item field-name))))

(defn field-renderers [page-spec]
  (reduce (fn [m {field-name :field :as field}]
            (assoc m field-name
                   (or (:render field)
                       (->renderer field-name
                                   (or (:format field)
                                       default-formatter)))))
          {}
          (:fields page-spec)))

(defn render-item-fields [page-spec item]
  (let [renderers (field-renderers page-spec)]
    (reduce-kv (fn [item' field-name field-value]
                 (if-let [renderer (get renderers field-name)]
                   (let [rendered (renderer item)]
                     (if (not= field-value rendered)
                       (assoc item'
                              (keyword "_rendered" (->str field-name))
                              rendered)
                       item'))
                   item'))
               item
               item)))

(defn make-api-routes [page-name page-spec adapter]
  (letfn [(run-op [op params]
            (try
              (op adapter params)
              (res/response {:status :ok})
              (catch Exception e
                ;; FIXME: add more proper status code
                (res/response {:status :failed :msg (.getMessage e)}))))]
   (routes
    (GET page-name {:keys [params]}
      (->> (adapter/read adapter params)
           (map #(render-item-fields page-spec %))
           res/response))
    (POST page-name {:keys [params]}
      (run-op adapter/create params))
    (PUT (str page-name "/:id") {:keys [params]}
      (run-op adapter/update params))
    (DELETE (str page-name "/:id") {:keys [params]}
      (run-op adapter/delete params))
    (GET (str page-name "/_spec") []
      (res/response (remove-fns page-spec))))))

(defn make-apis-handler [site-spec]
  (->> (for [[page-name {page-spec :spec adapter :adapter}] site-spec
             :let [page-name (str "/" (name page-name))]]
         (make-api-routes page-name page-spec adapter))
       (apply routes)))

(defn render-page [page]
  (-> (format "public/html/%s.html" page)
      res/resource-response
      (res/content-type "text/html")
      (res/charset "utf-8")))

(defn make-pages-handler [site-spec]
  (->> (for [[page-name _] site-spec
             :let [page-name (str "/" (name page-name))]]
         (GET page-name [] (render-page "page")))
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
