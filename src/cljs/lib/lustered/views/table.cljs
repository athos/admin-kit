(ns lustered.views.table
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]
            [lustered.utils :as utils]
            [lustered.views.utils :as views.utils]))

(defn edit-buttons [index item]
  (letfn [(on-edit [] (views.utils/open-modal index item))
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

(defn sortable-field [field-name field-label]
  (let [base-path (r/subscribe [:base-path])
        page-state (r/subscribe [:page-state])]
    (fn [field-name field-label]
      (let [{:keys [page-name order desc?] :as page-state} @page-state
            [state icon] (if (= order field-name)
                           [(update page-state :desc? not)
                            (if desc? :fa-sort-desc :fa-sort-asc)]
                           [(-> page-state
                                (assoc :order field-name :desc? false))
                            :fa-sort])
            state (dissoc state :page-no)
            uri (utils/page-state->uri @base-path state)
            on-click (fn [e]
                       (.preventDefault e)
                       (->> (apply concat state)
                            (apply handlers/move-to page-name)))]
        [:a.sortable {:href uri :on-click on-click}
         [:i.fa {:class icon}]
         field-label]))))

(defn table-header [fields]
  (fn [fields]
    [:thead
     [:tr
      (for [{:keys [name label detail? sortable?]} fields
            :when (not detail?)]
        ^{:key name} [:th (if sortable?
                            [sortable-field name label]
                            label)])
      [:th]]]))

(defn table-body [fields]
  (let [items (r/subscribe [:items])]
    (fn [fields]
      [:tbody
       (for [[index item] (map-indexed vector @items)]
         ^{:key index}
         [:tr
          (for [{:keys [name values detail?]} fields
                :when (not detail?)]
            (let [rendered (views.utils/rendered-value item name values)]
              ^{:key name}
              [:td {:dangerouslySetInnerHTML {:__html rendered}}]))
          (edit-buttons index item)])])))

(defn items-table []
  (let [spec (r/subscribe [:spec])]
    (fn []
      (let [fields (:fields @spec)]
        [:table.table.table-striped
         [table-header fields]
         [table-body fields]]))))
