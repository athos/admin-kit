(ns admin-kit.views.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [admin-kit.handlers :as handlers]
            [admin-kit.subs]
            [admin-kit.views.utils :as utils]
            [admin-kit.views.nav :as nav]
            [admin-kit.views.table :as table]
            [admin-kit.views.modal :as modal]))

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
      (let [new-item (->> (for [{:keys [name default]} (:fields @spec)
                                :when default]
                            [name default])
                          (into {}))]
        [:button.btn.btn-success.pull-right
         {:type :button :on-click #(utils/open-modal nil new-item)}
         [:i.fa.fa-plus-circle] " Add new"]))))

(def Pagination (reagent/adapt-react-class (.-Pagination js/ReactBootstrap)))

(defn pagination []
  (let [page-state (r/subscribe [:page-state])
        total-pages (r/subscribe [:total-pages])]
    (fn []
      (when (and @total-pages (> @total-pages 1))
        (let [on-select (fn [event selected-event]
                          (let [{:keys [page-name] :as state} @page-state
                                page-no (aget selected-event "eventKey")]
                            (->> (assoc state :page-no page-no)
                                 (apply concat)
                                 (apply handlers/move-to page-name))))]
          [:div.text-center
           [Pagination {:items @total-pages
                       :max-buttons 5
                       :prev true
                       :next true
                       :first true
                       :last true
                       :ellipsis true
                       :boundary-links true
                       :active-page (or (:page-no @page-state) 1)
                       :on-select on-select}]])))))

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
         (when (some-> @spec :supported-ops :create)
           [add-new-button])
         [error-alert]
         (when @spec
           [table/items-table])
         (when (some-> @spec :supported-ops :count)
           [pagination])]]
       (when @spec
         [modal/edit-modal])])))
