(ns lustered.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [ajax.core :as ajax]
            [clojure.string :as str]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;;
;; Handlers
;;

(r/register-handler
 :request
 [r/trim-v]
 (fn [db [page-name paths {:keys [method data]} callback]]
   (ajax/ajax-request
    (cond-> {:uri (str "/admin/api/" page-name
                       (if (empty? paths) "" (str "/" (str/join "/" paths))))
             :method method
             :handler (fn [[ok? data]] (if ok? (callback data))) ;; FIXME: should handle errors in a more proper way
             :format (ajax/transit-request-format)
             :response-format (ajax/transit-response-format)}
      data (assoc :params data)))
   db))

(defn request
  ([page-name paths callback]
   (request page-name paths {:method :get} callback))
  ([page-name paths opts callback]
   (prn page-name paths opts)
   (r/dispatch [:request page-name paths opts callback])))

(r/register-handler
 :save
 [r/trim-v]
 (fn [db [key val]]
   (assoc db key val)))

(defn save [key val]
  (r/dispatch [:save key val]))

(r/register-handler
 :init
 [r/trim-v]
 (fn [_ [page-name]]
   (request page-name ["_spec"]
            (fn [spec]
              (save :spec spec)
              (request page-name [] #(save :items (vec %)))))
   {:page-name page-name :modal-shown? false}))

(r/register-handler
 :edit-item-field
 [r/trim-v (r/path :editing-item)]
 (fn [editing-item [field value]]
   (assoc-in editing-item [:item field] value)))

(declare formatted-field?)

(defn preprocess-item-fields [item]
  (reduce-kv (fn [m k v]
               (if (formatted-field? k)
                 m
                 (assoc m k (str v))))
             {}
             item))

(r/register-handler
 :request-update-item
 [r/trim-v]
 (fn [{:keys [page-name] :as db} [index item callback]]
   (let [item' (preprocess-item-fields item)]
     (request page-name [(:id item)] {:method :put :data item'} callback))
   db))

(r/register-handler
 :update-item
 [r/trim-v (r/path :items)]
 (fn [items [index item]]
   (assoc items index item)))

;;
;; Subscriptions
;;

(r/register-sub
 :spec
 (fn [db _]
   (reaction (:spec @db))))

(r/register-sub
 :items
 (fn [db _]
   (reaction (:items @db))))

(r/register-sub
 :editing-item
 (fn [db _]
   (reaction (:editing-item @db))))

(r/register-sub
 :modal-shown?
 (fn [db _]
   (reaction (:modal-shown? @db))))

;;
;; Utilities
;;

(defn open-modal [index item]
  (save :editing-item {:index index :item item})
  (save :modal-shown? true))

(defn close-modal []
  (save :editing-item nil)
  (save :modal-shown? false))

;;
;; Components
;;

(defn edit-buttons [index item]
  (letfn [(on-edit [] (open-modal index item))]
    [:td
     [:button.btn.btn-success {:type "button" :on-click on-edit}
      "編集"]
     [:button.btn.btn-danger {:type "button"} "削除"]]))

(defn formatted-field? [field-name]
  (= (namespace field-name) "_formatted"))

(defn formatted-value [item field-name]
  (let [field-name' (keyword "_formatted" (name field-name))]
    (or (get item field-name')
        (get item field-name))))

(defn items-table []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (let [fields (:fields @spec)]
        [:table.table.table-striped
         [:thead
          [:tr
           (for [{:keys [field label]} fields]
             ^{:key field} [:th label])
           [:th "アクション"]]]
         (when @items
           [:tbody
            (for [[index item] (map-indexed vector @items)]
              ^{:key index}
              [:tr
               (for [{:keys [field]} fields]
                 ^{:key field} [:td (formatted-value item field)])
               (edit-buttons index item)])])]))))

(def FormControlsStatic
  (reagent/adapt-react-class (.. js/ReactBootstrap -FormControls -Static)))
(def Input
  (reagent/adapt-react-class (.. js/ReactBootstrap -Input)))

(defmulti render-field (fn [field value formatted updater] (:type field)))
(defmethod render-field :default [field value formatted _]
  [FormControlsStatic
   {:label (:label field)
    :label-class-name "col-xs-3"
    :wrapper-class-name "col-xs-9"
    :value formatted}])

(defmethod render-field :text [field value _ updater]
  (let [{field-name :field field-label :label} field]
    [Input {:type :text
            :label field-label
            :label-class-name "col-xs-3"
            :wrapper-class-name "col-xs-9"
            :placeholder field-label
            :default-value value
            :on-change (fn [e] (updater (.. e -target -value)))}]))

(defn modal-form [fields item]
  [:form.form-horizontal
   (for [{field-name :field :as field} fields]
     (letfn [(updater [val]
               (r/dispatch [:edit-item-field field-name val]))]
       (with-meta
         (render-field field
                       (get item field-name)
                       (formatted-value item field-name)
                       updater)
         {:key (name field-name)})))])

(defn modal-submit-button [editing-item]
  (letfn [(on-submit [_]
            (let [{:keys [index item]} @editing-item]
              (r/dispatch [:request-update-item index item
                           (fn [item']
                             (close-modal)
                             (r/dispatch [:update-item index item']))])))]
    [:button.btn.btn-primary {:type "button" :on-click on-submit}
     "Save changes"]))

(def Modal
  (reagent/adapt-react-class (.. js/ReactBootstrap -Modal)))
(def ModalHeader
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalHeader)))
(def ModalTitle
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalTitle)))
(def ModalBody
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalBody)))
(def ModalFooter
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalFooter)))

(defn edit-modal []
  (let [spec (r/subscribe [:spec])
        modal-shown? (r/subscribe [:modal-shown?])
        editing-item (r/subscribe [:editing-item])]
    (fn []
      [Modal {:show @modal-shown? :on-hide close-modal}
       [ModalHeader {:close-button true}
        [ModalTitle (:title @spec)]]
       (when-let [{:keys [item]} @editing-item]
         [ModalBody (modal-form (:fields @spec) item)])
       [ModalFooter
        [:button.btn.btn-default {:type "button" :on-click close-modal}
         "Close"]
        [modal-submit-button editing-item]]])))

(defn app []
  (let [spec (r/subscribe [:spec])]
    (fn []
      (when @spec
        [:div
         [:div.row
          [:div.col-md-1]
          [:div.col-md-10
           [:h1 (:title @spec)]
           [items-table]]
          [:div.col-md-1]]
         [edit-modal]]))))

;;
;; Entry point
;;

(defn ^:export main []
  (r/dispatch [:init "products"])
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload [])
