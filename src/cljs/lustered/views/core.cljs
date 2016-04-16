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
      (let [new-item (->> (for [{:keys [field default]} (:fields @spec)
                                :when default]
                            [field default])
                          (into {}))]
        [:button.btn.btn-success.pull-right
         {:type :button :on-click #(utils/open-modal nil new-item)}
         [:i.fa.fa-plus-square-o] " Add new"]))))

(def Pagination (reagent/adapt-react-class (.-Pagination js/ReactBootstrap)))

(defn pagination []
  (let [page-state (r/subscribe [:page-state])
        total-pages (r/subscribe [:total-pages])]
    (fn []
      (when @total-pages
        (let [{:keys [page-name page-no]} @page-state
              on-select (fn [event selected-event]
                          (let [page-no (.-eventKey selected-event)]
                            (handlers/move-to page-name :page-no page-no)))]
          [Pagination {:items @total-pages
                       :max-buttons 5
                       :prev true
                       :next true
                       :first true
                       :last true
                       :ellipsis true
                       :boundary-links true
                       :active-page page-no
                       :on-select on-select}])))))

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
         [pagination]
         [add-new-button]]]
       (when @spec
         [modal/edit-modal])])))
