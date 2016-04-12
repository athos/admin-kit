(ns lustered.views.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]
            [lustered.views.utils :as utils]
            [lustered.views.nav :as nav]
            [lustered.views.table :as table]
            [lustered.views.modal :as modal]))

(def Alert (reagent/adapt-react-class (.-Alert js/ReactBootstrap)))

(defn error-alert []
  (let [errors (r/subscribe [:errors])]
    (fn []
      (when @errors
        [Alert {:bs-style :danger :on-dismiss #(handlers/save :errors nil)}
         (for [error @errors]
           ^{:keys error} [:p error])]))))

(defn add-new-button []
  (let [spec (r/subscribe [:spec])]
    (fn []
      (let [new-item (->> (for [{:keys [field default]} (:fields spec)
                                :when default]
                            [field default])
                          (into {}))]
        [:button.btn.btn-success.pull-right
         {:type :button :on-click #(utils/open-modal nil new-item)}
         [:i.fa.fa-plus-square-o] " Add new"]))))

(defn app []
  (let [spec (r/subscribe [:spec])
        errors (r/subscribe [:errors])]
    (fn []
      [:div
       [:div.row
        [:div.col-md-3
         [nav/pages-navigation]]
        [:div.col-md-9
         (when @spec
           [:h1 (:title @spec)])
         [error-alert]
         (when @spec
           [table/items-table])
         [add-new-button]]]
       (when @spec
         [modal/edit-modal])])))
