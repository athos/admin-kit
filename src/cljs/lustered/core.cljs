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
 :fetch
 [r/trim-v]
 (fn [db [page-name paths callback]]
   (ajax/ajax-request
    {:uri (cond-> (str "/admin/api/" page-name)
            (not (empty? paths)) (str "/" (str/join "/" paths)))
     :method :get
     :handler (fn [[ok? data]] (callback data))
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)})
   db))

(defn fetch [page-name paths callback]
  (r/dispatch [:fetch page-name paths callback]))

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
   (fetch page-name ["_spec"]
          (fn [spec]
            (save :spec spec)
            (fetch page-name [] #(save :items %))))
   {:modal-shown? false}))

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

(defn formatted-value [item field-name]
  (let [field-name' (keyword "_formatted" (name field-name))]
    (or (get item field-name')
        (get item field-name))))

(defn items-table [spec items]
  (let [fields (:fields spec)]
    `[:table.table.table-striped
      [:thead
       [:tr
        ~@(for [{:keys [label]} fields]
            [:th label])
        [:th "アクション"]]]
      ~(when items
         `[:tbody
           ~@(for [[index item] (map-indexed vector items)]
               `[:tr
                 ~@(for [{:keys [field]} fields]
                     [:td (formatted-value item field)])
                 ~[edit-buttons index item]])])]))

(def FormControlsStatic
  (reagent/adapt-react-class (.. js/ReactBootstrap -FormControls -Static)))
(def Input
  (reagent/adapt-react-class (.. js/ReactBootstrap -Input)))

(defmulti render-field (fn [field value formatted] (:type field)))
(defmethod render-field :default [field value formatted]
  [FormControlsStatic
   {:label (:label field)
    :label-class-name "col-xs-3"
    :wrapper-class-name "col-xs-9"
    :value formatted}])

(defmethod render-field :text [field value _]
  (let [{field-name :field field-label :label} field]
    [Input {:type :text
            :label field-label
            :label-class-name "col-xs-3"
            :wrapper-class-name "col-xs-9"
            :placeholder field-label
            :value value}]))

(defn modal-form [fields item]
  [:form.form-horizontal
   (for [{field-name :field :as field} fields]
     (with-meta
       (render-field field
                     (get item field-name)
                     (formatted-value item field-name))
       {:key (name field-name)}))])

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
        [:button.btn.btn-primary {:type "button"} "Save changes"]]])))

(defn app []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (when @spec
        [:div
         [:div.row
          [:div.col-md-1]
          [:div.col-md-10
           [:h1 (:title @spec)]
           [items-table @spec @items]]
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
