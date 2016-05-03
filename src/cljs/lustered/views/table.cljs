(ns lustered.views.table
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]
            [lustered.views.utils :as utils]))

(defn edit-buttons [index item]
  (letfn [(on-edit [] (utils/open-modal index item))
          (on-delete []
            (when (js/confirm "Are you sure you want to delete the item?")
              (r/dispatch [:request-delete-item item])))]
    [:td
     [:a {:href "#" :on-click on-edit}
      [:span.fa-stack.fa-lg
       [:i.fa.fa-circle.fa-stack-2x.text-primary]
       [:i.fa.fa-pencil.fa-stack-1x.fa-inverse]]]
     [:a {:href "#" :on-click on-delete}
      [:span.fa-stack.fa-lg
       [:i.fa.fa-circle.fa-stack-2x.text-danger]
       [:i.fa.fa-trash.fa-stack-1x.fa-inverse]]]]))

(defn table-header [fields]
  (let [page-state (r/subscribe [:page-state])]
    (fn [fields]
      (let [{:keys [page-name page-number order desc?]} @page-state]
        [:thead
         [:tr
          (for [{:keys [name label detail? sortable?]} fields
                :when (not detail?)]
            (with-meta `[:th ~@(when sortable?
                                 [{:class :sortable}
                                  (if (= order name)
                                    (if desc?
                                      [:i.fa.fa-sort-desc]
                                      [:i.fa.fa-sort-asc])
                                    [:i.fa.fa-sort])])
                         ~label]
              {:key name}))
          [:th]]]))))

(defn table-body [fields items]
  (fn [fields items]
    [:tbody
     (for [[index item] (map-indexed vector @items)]
       ^{:key index}
       [:tr
        (for [{:keys [name values detail?]} fields
              :when (not detail?)]
          (let [rendered (utils/rendered-value item name values)]
            ^{:key name} [:td rendered]))
        (edit-buttons index item)])]))

(defn items-table []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (let [fields (:fields @spec)]
        [:table.table.table-striped
         [table-header fields]
         (when @items
           [table-body fields items])]))))
