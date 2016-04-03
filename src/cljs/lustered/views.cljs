(ns lustered.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]))

;;
;; Utilities
;;

(defn open-modal [index item]
  (handlers/save :editing-item {:index index :item item})
  (handlers/save :modal-shown? true))

(defn close-modal []
  (handlers/save :editing-item nil)
  (handlers/save :modal-shown? false))

;;
;; Components
;;

(defn edit-buttons [index item]
  (letfn [(on-edit [] (open-modal index item))]
    [:td
     [:a {:href "#" :on-click on-edit}
      [:span.fa-stack.fa-lg
       [:i.fa.fa-circle.fa-stack-2x.text-primary]
       [:i.fa.fa-pencil.fa-stack-1x.fa-inverse]]]
     [:a {:href "#"}
      [:span.fa-stack.fa-lg
       [:i.fa.fa-circle.fa-stack-2x.text-danger]
       [:i.fa.fa-trash.fa-stack-1x.fa-inverse]]]]))

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
           [:th]]]
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
