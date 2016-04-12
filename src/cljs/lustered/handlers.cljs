(ns lustered.handlers
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            [clojure.string :as str]))

(r/register-handler
 :request
 [r/trim-v]
 (fn [db [paths {:keys [method data]} callback]]
   (ajax/ajax-request
    (cond-> {:uri (str "/admin/api"
                       (if (empty? paths) "" (str "/" (str/join "/" paths))))
             :method method
             :handler (fn [[ok? data]] (callback ok? data))
             :format (ajax/transit-request-format)
             :response-format (ajax/transit-response-format)}
      data (assoc :params data)))
   db))

(defn request
  ([paths callback]
   (request paths {:method :get} callback))
  ([paths opts callback]
   (r/dispatch [:request paths opts callback])))

(r/register-handler
 :save
 [r/trim-v]
 (fn [db [key val]]
   (assoc db key val)))

(defn save [key val]
  (r/dispatch [:save key val]))

(defn error [res]
  (save :errors [(:msg res)]))

(defn edit-error [res]
  (save :edit-errors [(:msg res)]))

(defn wrap-with-error-handler [handler f]
  (fn [ok? {:keys [status] :as res}]
    (if ok?
      (if (= status :ok)
        (f res)
        (handler res))  ;; this should not happen
      (handler (:response res)))))

(r/register-handler
 :init
 [r/trim-v]
 (fn [_ [base-path callback]]
   (request [] (wrap-with-error-handler error
                 (fn [{:keys [overview]}]
                   (save :pages overview)
                   (callback))))
   {:base-path base-path}))

(defn init [base-path callback]
  (r/dispatch [:init base-path callback]))

(r/register-handler
 :fetch-items
 [r/trim-v]
 (fn [db [page-name]]
   (request [page-name]
            (wrap-with-error-handler error
              (fn [{:keys [items]}]
                (save :items items))))
   db))

(defn fetch-items [page-name]
  (r/dispatch [:fetch-items page-name]))

(r/register-handler
 :page-init
 [r/trim-v]
 (fn [db [page-name]]
   (request [page-name "_spec"]
            (wrap-with-error-handler error
              (fn [{:keys [spec]}]
                (save :spec spec)
                (fetch-items page-name))))
   (-> db
       (dissoc :spec :items)
       (assoc :page-name page-name)
       (assoc :modal-shown? false))))

(defn page-init [page-name]
  (r/dispatch [:page-init page-name]))

(r/register-handler
 :edit-item-field
 [r/trim-v (r/path :editing-item)]
 (fn [editing-item [field value]]
   (assoc-in editing-item [:item field] value)))

(defn rendered-field? [field-name]
  (= (namespace field-name) "_rendered"))

(defn preprocess-item-fields [item]
  (reduce-kv (fn [m k v]
               (if (rendered-field? k)
                 m
                 (assoc m k (str v))))
             {}
             item))

(r/register-handler
 :request-create-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [item callback]]
   (let [item' (preprocess-item-fields item)]
     (request [page-name] {:method :post :data item'}
              (wrap-with-error-handler edit-error
                (fn [_]
                  (fetch-items page-name)
                  (callback))))
     db)))

(r/register-handler
 :request-update-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [index item callback]]
   (let [item' (preprocess-item-fields item)]
     (request [page-name (:id item)] {:method :put :data item'}
              (wrap-with-error-handler edit-error
                (fn [_]
                  (fetch-items page-name)
                  (callback)))))
   db))

(r/register-handler
 :request-delete-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [item]]
   (request [page-name (:id item)] {:method :delete}
            (wrap-with-error-handler error
              (fn [_]
                (fetch-items page-name))))
   db))
