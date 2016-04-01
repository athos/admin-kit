(ns lustered.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [ajax.core :as ajax]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(r/register-handler
 :init
 (fn [_ _]
   (r/dispatch [:fetch-spec])
   {}))

(r/register-handler
 :fetch-spec
 [(r/path :spec)]
 (fn [_ _]
   (ajax/ajax-request
    {:uri "/admin/api/products/_spec"
     :method :get
     :handler (fn [[ok? spec]] (r/dispatch [:save-spec spec]))
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)})
   nil))

(r/register-handler
 :save-spec
 [r/trim-v (r/path :spec)]
 (fn [_ [spec]]
   (r/dispatch [:fetch-items (:name spec)])
   spec))

(r/register-handler
 :fetch-items
 [r/trim-v (r/path :items)]
 (fn [_ [page-name]]
   (ajax/ajax-request
    {:uri (str "/admin/api/" (name page-name))
     :method :get
     :handler (fn [[ok? items]] (r/dispatch [:save-items items]))
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)})
   nil))

(r/register-handler
 :save-items
 [r/trim-v (r/path :items)]
 (fn [_ [items]]
   items))

(r/register-sub
 :spec
 (fn [db _]
   (reaction (:spec @db))))

(r/register-sub
 :items
 (fn [db _]
   (reaction (:items @db))))

(defn app []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (when @spec
        (let [fields (:fields @spec)]
          `[:div.row
            [:div.col-md-1]
            [:div.col-md-10
             [:h1 ~(:title @spec)]
             [:table.table.table-striped
              [:thead
               [:tr
                ~@(for [{:keys [title]} fields]
                    [:th title])]]
              ~(when @items
                 `[:tbody
                   ~@(for [item @items]
                       `[:tr
                         ~@(for [{:keys [field]} fields]
                             [:td (get item field)])])])]]
            [:div.col-md-1]])))))

(defn ^:export main []
  (r/dispatch [:init])
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
