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

(defn replace-values-fn [page-spec]
  (->> (for [{:keys [values] :as field} (:fields page-spec)]
         (if (and values (fn? values))
           (assoc field :values (values))
           field))
       (assoc page-spec :fields)))

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
                                       (some->> (:values field) (into {}))
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

(defmacro with-error-handling [& body]
  `(letfn [(error-response# [status# ^Exception e#]
             (-> (res/response {:status :failed :msg (.getMessage e#)})
                 (res/status status#)))]
     (try
       ~@body
       (catch IllegalArgumentException e#
         (error-response# 400 e#))
       (catch Exception e#
         (error-response# 500 e#)))))

(defn respond [& {:as args}]
  (res/response (merge {:status :ok} args)))

(defn handle-read [page-spec adapter params {:keys [items-per-page]
                                             :or {items-per-page 10}}]
  (letfn [(->long [x] (if (string? x) (Long/parseLong x) x))
          (normalize-params [params]
            (let [offset (or (some-> (:_offset params) ->long)
                             (some-> (:_page params)
                                     ->long
                                     (* items-per-page))
                             0)
                  limit (or (some-> (:_limit params) ->long)
                            items-per-page)]
              (-> params
                  (assoc :_offset offset :_limit limit)
                  (dissoc :_page))))]
    (with-error-handling
      (let [params (normalize-params params)
            items (->> (adapter/read adapter params)
                       (map #(render-item-fields page-spec %)))]
        (if (satisfies? adapter/Count adapter)
          (respond :items items :count (adapter/count adapter params))
          (respond :items items))))))

(defn make-api-routes [page-name page-spec adapter config]
  (letfn [(run-op [op params]
            (with-error-handling
              (op adapter params)
              (respond)))]
   (routes
    (GET page-name {:keys [params]}
      (handle-read page-spec adapter params config))
    (POST page-name {:keys [params]}
      (run-op adapter/create params))
    (PUT (str page-name "/:id") {:keys [params]}
      (run-op adapter/update params))
    (DELETE (str page-name "/:id") {:keys [params]}
      (run-op adapter/delete params))
    (GET (str page-name "/_spec") []
      (with-error-handling
        (->> page-spec
             replace-values-fn
             remove-fns
             (respond :spec)))))))

(defn make-root-api-handler [site-spec]
  (let [site-overview (mapv (fn [[page-name {:keys [spec]}]]
                              {:name page-name :title (:title spec)})
                            site-spec)]
    (GET "/" []
      (with-error-handling
        (respond :overview site-overview)))))

(defn make-apis-handler [site-spec config]
  (->> (for [[page-name {page-spec :spec adapter :adapter}] site-spec
             :let [page-name (str "/" (name page-name))]]
         (make-api-routes page-name page-spec adapter config))
       (apply routes (make-root-api-handler site-spec))))

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

(defn make-admin-site-handler
  ([site-spec] (make-admin-site-handler site-spec {}))
  ([site-spec config]
   (routes
    (-> (context "/api" []
          (make-apis-handler site-spec config))
        (wrap-restful-format :formats [:transit-json])
        (wrap-defaults api-defaults))
    (-> (routes
         (GET "/" [] (render-page "root"))
         (context "/pages" []
           (make-pages-handler site-spec))
         (route/resources "public"))
        (wrap-defaults site-defaults)))))
