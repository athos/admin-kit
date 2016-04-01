(ns lustered.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [ajax.core :as ajax]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(declare fetch-spec)

(r/register-handler
 :init
 (fn [_ _]
   (fetch-spec "products")
   {}))

(r/register-handler
 :fetch
 [r/trim-v]
 (fn [db [path cont & args]]
   (ajax/ajax-request
    {:uri (str "/admin/api" path)
     :method :get
     :handler (fn [[ok? data]] (r/dispatch `[~cont ~data ~@args]))
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)})
   db))

(defn fetch-spec [page-name]
  (r/dispatch [:fetch (str "/" page-name "/_spec") :save-spec]))

(declare fetch-items)

(r/register-handler
 :save-spec
 [r/trim-v (r/path :spec)]
 (fn [_ [spec]]
   (fetch-items (name (:name spec)))
   spec))

(defn fetch-items [page-name]
  (r/dispatch [:fetch (str "/" page-name) :save-items]))

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

(defn items-table [spec items]
  (let [fields (:fields spec)]
    `[:table.table.table-striped
      [:thead
       [:tr
        ~@(for [{:keys [title]} fields]
            [:th title])]]
      ~(when items
         `[:tbody
           ~@(for [item items]
               `[:tr
                 ~@(for [{:keys [field]} fields]
                     [:td (get item field)])])])]))

(defn app []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (when @spec
        [:div.row
         [:div.col-md-1]
         [:div.col-md-10
          [:h1 (:title @spec)]
          [items-table @spec @items]]
         [:div.col-md-1]]))))

(defn ^:export main []
  (r/dispatch [:init])
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
