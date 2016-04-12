(ns lustered.views.modal
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.subs]
            [lustered.views.utils :as utils]
            [lustered.views.forms :as forms]))

(defn modal-form [fields item]
  [:form.form-horizontal
   (for [{field-name :field :as field} fields]
     (letfn [(updater [val]
               (r/dispatch [:edit-item-field field-name val]))]
       (with-meta
         (forms/render-field field
                             (get item field-name)
                             (utils/rendered-value item field-name
                                                   (:values field))
                             updater)
         {:key (name field-name)})))])

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
       (when-let [{:keys [item]} @editing-item]
         [ModalBody (modal-form (:fields @spec) item)])
       [ModalFooter
        [:button.btn.btn-default {:type "button" :on-click utils/close-modal}
         "Close"]
        [modal-submit-button editing-item]]])))
