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
  (let [spec (r/subscribe [:spec])]
    (fn []
      (when @spec
        [:div
         [:div.row
          [:div.col-md-3
           [nav/pages-navigation]]
          [:div.col-md-9
           [:h1 (:title @spec)]
           [table/items-table]
           [add-new-button]]]
         [modal/edit-modal]]))))
