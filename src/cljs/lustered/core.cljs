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
   (ajax/ajax-request
    {:uri "/admin/api/products"
     :method :get
     :handler (fn [[ok? result]] (r/dispatch [:post-init result]))
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)})
   {}))

(r/register-handler
 :post-init
 [r/trim-v]
 (fn [db [data]]
   (assoc db :data data)))

(r/register-sub
 :data
 (fn [db _]
   (reaction (:data @db))))

(defn app []
  (let [data (r/subscribe [:data])]
    (fn []
      (when @data
        `[:ul
          ~@(for [{:keys [name]} @data]
              [:li name])]))))

(defn ^:export main []
  (r/dispatch [:init])
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
