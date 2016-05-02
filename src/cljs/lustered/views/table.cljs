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

(defn items-table []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (let [fields (:fields @spec)]
        [:table.table.table-striped
         [:thead
          [:tr
           (for [{:keys [name label detail? sortable?]} fields
                 :when (not detail?)]
             ^{:key name} `[:th ~@(when sortable?
                                    [{:class :sortable} [:i.fa.fa-sort]])
                            ~label])
           [:th]]]
         (when @items
           [:tbody
            (for [[index item] (map-indexed vector @items)]
              ^{:key index}
              [:tr
               (for [{:keys [name values detail?]} fields
                     :when (not detail?)]
                 (let [rendered (utils/rendered-value item name values)]
                   ^{:key name} [:td rendered]))
               (edit-buttons index item)])])]))))
