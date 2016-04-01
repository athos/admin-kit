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
   spec))

(r/register-sub
 :spec
 (fn [db _]
   (reaction (:spec @db))))

(defn app []
  (let [spec (r/subscribe [:spec])]
    (fn []
      (when @spec
        `[:div
          [:h1 ~(:title @spec)]
          [:table
           [:thead
            [:tr
             ~@(for [{:keys [title]} (:fields @spec)]
                 [:th title])]]]]))))

(defn ^:export main []
  (r/dispatch [:init])
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
