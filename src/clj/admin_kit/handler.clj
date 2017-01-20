(ns admin-kit.handler
  (:require [ring.util.response :as res]
            [compojure
             [core :refer :all]
             [route :as route]]
            [ring.middleware
             [defaults :refer [wrap-defaults site-defaults api-defaults]]]
            [ring.middleware
             [format-params :refer [wrap-restful-params]]
             [format-response :refer [wrap-restful-response]]]
            [clojure.java.io :as io]
            [clojure
             [walk :as walk]
             [string :as str]]
            [admin-kit
             [adapter :as adapter]
             [transit :as transit]]))

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
  (letfn [(escape-html [text]
            (some-> text
                    (str/replace "&" "&amp;")
                    (str/replace "<" "&lt;")
                    (str/replace ">" "&gt;")
                    (str/replace "\"" "&quot;")
                    (str/replace "'" "&apos;")))]
    (fn [item]
      (escape-html (formatter (get item field-name))))))

(defn field-renderers [page-spec]
  (reduce (fn [m {field-name :name :as field}]
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
                            (keyword "_rendered" (name field-name))
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
       (response 400 :error {:msg (.getMessage e#)}))
     (catch Throwable e#
       (response 500 :error {:msg (.getMessage e#)}))))

(defn prepare-params [params {:keys [items-per-page]}]
  (let [->long (fn [x] (if (string? x) (Long/parseLong x) x))
        offset (or (some-> (:_offset params) ->long)
                   (some-> (:_page params)
                           ->long
                           dec
                           (* items-per-page))
                   0)
        limit (or (some-> (:_limit params) ->long)
                  items-per-page)
        order {:field (or (keyword (:_order params)) :_id)
               :desc? (boolean (:_desc params))}]
    (-> params
        (assoc :_offset offset :_limit limit :_order order)
        (dissoc :_page :_desc?))))

(defn handle-read [page-spec adapter params config]
  (with-error-handling
    (let [config (merge {:items-per-page 10} config)
          params (prepare-params params config)
          renderers (field-renderers (replace-values-fn page-spec))
          items (->> (adapter/read adapter params)
                     (map #(render-item-fields renderers %)))]
      (-> (cond-> {:items items}
            (contains? (adapter/supported-ops adapter) :count)
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
              (let [result (and validator (validator params))]
                (if (empty? result)
                  (f)
                  (response 400 :validation-failed {:errors result})))))]
   (routes
    (GET page-name {:keys [params]}
      (handle-read page-spec adapter params config))
    (POST page-name {:keys [params]}
      (with-validation params
        #(run-op adapter/create params)))
    (PUT page-name {:keys [params]}
      (with-validation params
        #(run-op adapter/update params)))
    (DELETE page-name {:keys [params]}
      (run-op adapter/delete params))
    (let [ops (adapter/supported-ops adapter)]
      (GET (str page-name "/_spec") []
        (with-error-handling
          (->> (assoc page-spec :supported-ops ops)
               replace-values-fn
               remove-fns
               (array-map :spec)
               response)))))))

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

(defn render-page [page {:keys [html-path]}]
  (-> (or html-path (format "public/html/%s.html" page))
      res/resource-response
      (res/content-type "text/html")
      (res/charset "utf-8")))

(defn make-pages-handler [site-spec config]
  (->> (for [[page-name _] site-spec
             :let [page-name (str "/" (name page-name))]]
         (GET page-name [] (render-page "page" config)))
       (apply routes)))

(defn make-admin-site-handler
  ([site-spec] (make-admin-site-handler site-spec {}))
  ([site-spec config]
   (routes
    (-> (context "/api" []
          (make-apis-handler site-spec config))
        (wrap-restful-params
          :formats [:transit-json]
          :format-options {:transit-json {:handlers transit/read-handlers}})
        (wrap-restful-response
          :formats [:transit-json]
          :format-options {:transit-json {:handlers transit/write-handlers}})
        (wrap-defaults api-defaults))
    (-> (routes
         (GET "/" [] (render-page "root" config))
         (context "/pages" []
           (make-pages-handler site-spec config))
         (route/resources "/_admin-kit"))
        (wrap-defaults site-defaults)))))
