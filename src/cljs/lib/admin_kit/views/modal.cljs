(ns admin-kit.views.modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [admin-kit.handlers :as handlers]
            [admin-kit.subs]
            [admin-kit.views.utils :as utils]
            [admin-kit.views.forms :as forms]))

(defn modal-form []
  (let [spec (r/subscribe [:spec])
        errors (r/subscribe [:validation-errors])
        editing-item (r/subscribe [:editing-item])]
    (fn []
      (let [fields (:fields @spec)
            {:keys [item]} @editing-item]
        [:form.form-horizontal
         (-> (for [{field-name :name field-label :label :as field} fields]
               (let [errors (get @errors field-name)
                     updater #(r/dispatch [:edit-item-field field-name %])]
                 ^{:key field-name}
                 [:div.form-group {:class (when errors :has-error)}
                  [:label.control-label.col-xs-3
                   [:span field-label]]
                  [:div.col-xs-9
                   (forms/render-field field
                                       (get item field-name)
                                       (utils/rendered-value item
                                                             field-name
                                                             (:values field))
                                       updater)
                   (when errors
                     [:span.help-block (apply str errors)])]]))
             doall)]))))

(defn modal-submit-button [editing-item]
  (letfn [(on-submit [_]
            (let [{:keys [index item]} @editing-item]
              (if index
                (r/dispatch [:request-update-item index item
                             #(utils/close-modal)])
                (r/dispatch [:request-create-item item
                             #(utils/close-modal)]))))]
    [:button.btn.btn-primary {:type "button" :on-click on-submit}
     "Save"]))

(def Alert
  (reagent/adapt-react-class (.. js/ReactBootstrap -Alert)))

(defn edit-error-alert []
  (let [edit-errors (r/subscribe [:edit-errors])]
    (fn []
      (when @edit-errors
        [Alert {:bs-style :danger
                :on-dismiss #(handlers/save :edit-errors nil)}
         (for [error @edit-errors]
           ^{:key error} [:p error])]))))

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
      [Modal {:show @modal-shown? :on-hide utils/close-modal}
       [ModalHeader {:close-button true}
        [ModalTitle (:title @spec)]]
       (when @editing-item
         [ModalBody
          [edit-error-alert]
          [modal-form]])
       [ModalFooter
        [:button.btn.btn-default {:type "button" :on-click utils/close-modal}
         "Close"]
        [modal-submit-button editing-item]]])))
