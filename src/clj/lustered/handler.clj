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

(defn render-item-fields [renderers item]
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
             item))

(defn response
  ([params] (response :ok params))
  ([status params] (response 200 status params))
  ([code status params]
   (-> (res/response (merge {:status status} params))
       (res/status code))))

(defmacro with-error-handling [& body]
  `(try
     ~@body
     (catch IllegalArgumentException e#
       (response 400 :error (.getMessage e#)))
     (catch Exception e#
       (response 500 :error (.getMessage e#)))))

(defn normalize-params [params {:keys [items-per-page]}]
  (let [->long (fn [x] (if (string? x) (Long/parseLong x) x))
        offset (or (some-> (:_offset params) ->long)
                   (some-> (:_page params)
                           ->long
                           dec
                           (* items-per-page))
                   0)
        limit (or (some-> (:_limit params) ->long)
                  items-per-page)]
    (-> params
        (assoc :_offset offset :_limit limit)
        (dissoc :_page))))

(defn handle-read [page-spec adapter params config]
  (with-error-handling
    (let [config (merge {:items-per-page 10} config)
          params (normalize-params params config)
          renderers (field-renderers (replace-values-fn page-spec))
          items (->> (adapter/read adapter params)
                     (map #(render-item-fields renderers %)))]
      (-> (cond-> {:items items}
            (satisfies? adapter/Count adapter)
            #_=> (assoc :total-pages
                        (-> (adapter/count adapter params)
                            (/ (double (:items-per-page config)))
                            Math/ceil
                            long)))
          response))))

(defn make-api-routes [page-name page-spec adapter validator config]
  (letfn [(run-op [op params]
            (with-error-handling
              (op adapter params)
              (response {})))
          (with-validation [params f]
            (with-error-handling
              (if-let [result (and validator (validator params))]
                (response 400 :validation-failed {:errors result})
                (f))))]
   (routes
    (GET page-name {:keys [params]}
      (handle-read page-spec adapter params config))
    (POST page-name {:keys [params]}
      (with-validation params
        #(run-op adapter/create params)))
    (PUT (str page-name "/:id") {:keys [params]}
      (with-validation params
        #(run-op adapter/update params)))
    (DELETE (str page-name "/:id") {:keys [params]}
      (run-op adapter/delete params))
    (GET (str page-name "/_spec") []
      (with-error-handling
        (->> page-spec
             replace-values-fn
             remove-fns
             (array-map :spec)
             response))))))

(defn make-root-api-handler [site-spec]
  (let [site-overview (mapv (fn [[page-name {:keys [spec]}]]
                              {:name page-name :title (:title spec)})
                            site-spec)]
    (GET "/" []
      (with-error-handling
        (response {:overview site-overview})))))

(defn make-apis-handler [site-spec config]
  (->> (for [[page-name {page-spec :spec :keys [adapter validator]}] site-spec
             :let [page-name (str "/" (name page-name))]]
         (make-api-routes page-name page-spec adapter validator config))
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
