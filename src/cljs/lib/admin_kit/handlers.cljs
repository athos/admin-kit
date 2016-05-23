(ns admin-kit.handlers
  (:require [re-frame.core :as r]
            [ajax.core :as ajax]
            [clojure.string :as str]
            [admin-kit.utils :as utils]
            [admin-kit.transit :as transit]))

(r/register-handler
 :request
 [r/trim-v]
 (fn [{:keys [base-path] :as db} [paths {:keys [method data]} callback]]
   (ajax/ajax-request
    (cond-> {:uri (str base-path "/api"
                       (if (empty? paths) "" (str "/" (str/join "/" paths))))
             :method method
             :handler (fn [[ok? data]] (callback ok? data))
             :format (ajax/transit-request-format {:handlers transit/write-handlers})
             :response-format (ajax/transit-response-format {:handlers transit/read-handlers})}
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
  (if (= (:status res) :validation-failed)
    (save :validation-errors (:errors res))
    (save :edit-errors [(:msg res)])))

(defn wrap-with-error-handler [handler f]
  (fn [ok? {:keys [status] :as res}]
    (if ok?
      (if (= status :ok)
        (f res)
        (handler res))  ;; this should not happen
      (handler (:response res)))))

(defn init [base-path callback]
  (save :base-path base-path)
  (request [] (wrap-with-error-handler error
                (fn [{:keys [overview]}]
                  (save :pages overview)
                  (callback)))))

(r/register-handler
 :fetch-items
 [r/trim-v]
 (fn [db [page-name page-no order desc?]]
   (request [page-name]
            {:method :get
             :data (cond-> {}
                     page-no (assoc :_page page-no)
                     order (assoc :_order (name order))
                     desc? (assoc :_desc "true"))}
            (wrap-with-error-handler error
              (fn [{:keys [items total-pages]}]
                (save :items items)
                (save :total-pages total-pages))))
   db))

(defn fetch-items [{:keys [page-name page-no order desc?]}]
  (r/dispatch [:fetch-items page-name page-no order desc?]))

(r/register-handler
 :page-init
 [r/trim-v]
 (fn [db [{:keys [page-name] :as page-state}]]
   (request [page-name "_spec"]
            (wrap-with-error-handler error
              (fn [{:keys [spec]}]
                (save :spec spec)
                (fetch-items page-state))))
   (-> db
       (dissoc :spec :items)
       (assoc :page-state page-state)
       (assoc :modal-shown? false))))

(defn page-init [page-state]
  (r/dispatch [:page-init page-state]))

(r/register-handler
 :move-to
 [r/trim-v]
 (fn [{:keys [base-path page-state] :as db} [page-name page-no order desc?]]
   (let [page-state' (cond-> {:page-name page-name}
                       page-no (assoc :page-no page-no)
                       order (assoc :order order)
                       desc? (assoc :desc? desc?))
         path (utils/page-state->uri base-path page-state')]
     (.pushState js/history (clj->js page-state') nil path)
     (if (= (:page-name page-state) page-name)
       (fetch-items page-state')
       (page-init page-state'))
     (assoc db :page-state page-state'))))

(defn move-to [page-name & {:keys [page-no order desc?]}]
  (r/dispatch [:move-to page-name page-no order desc?]))

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
                 (assoc m k v)))
             {}
             item))

(r/register-handler
 :request-create-item
 [r/trim-v]
 (fn [{:keys [page-state] :as db} [item callback]]
   (let [item' (preprocess-item-fields item)]
     (request [(:page-name page-state)] {:method :post :data item'}
              (wrap-with-error-handler edit-error
                (fn [_]
                  (fetch-items page-state)
                  (callback))))
     db)))

(r/register-handler
 :request-update-item
 [r/trim-v]
 (fn [{:keys [page-state] :as db} [index item callback]]
   (let [item' (preprocess-item-fields item)]
     (request [(:page-name page-state)] {:method :put :data item'}
              (wrap-with-error-handler edit-error
                (fn [_]
                  (fetch-items page-state)
                  (callback)))))
   db))

(r/register-handler
 :request-delete-item
 [r/trim-v]
 (fn [{:keys [page-state] :as db} [{:keys [_id]}]]
   (request [(:page-name page-state)] {:method :delete :data {:_id _id}}
            (wrap-with-error-handler error
              (fn [_]
                (fetch-items page-state))))
   db))
