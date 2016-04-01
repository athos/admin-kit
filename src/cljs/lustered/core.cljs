(ns lustered.core
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [ajax.core :as ajax]
            [clojure.string :as str]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")


(r/register-handler
 :fetch
 [r/trim-v]
 (fn [db [page-name paths callback]]
   (ajax/ajax-request
    {:uri (cond-> (str "/admin/api/" page-name)
            (not (empty? paths)) (str "/" (str/join "/" paths)))
     :method :get
     :handler (fn [[ok? data]] (callback data))
     :format (ajax/transit-request-format)
     :response-format (ajax/transit-response-format)})
   db))

(defn fetch [page-name paths callback]
  (r/dispatch [:fetch page-name paths callback]))

(r/register-handler
 :save
 [r/trim-v]
 (fn [db [key val]]
   (assoc db key val)))

(defn save [key val]
  (r/dispatch [:save key val]))


(r/register-handler
 :init
 [r/trim-v]
 (fn [_ [page-name]]
   (fetch page-name ["_spec"]
          (fn [spec]
            (save :spec spec)
            (fetch page-name [] #(save :items %))))
   {}))

(r/register-sub
 :spec
 (fn [db _]
   (reaction (:spec @db))))

(r/register-sub
 :items
 (fn [db _]
   (reaction (:items @db))))

(defn edit-buttons []
  [:td
   [:button.btn.btn-success {:type "button"
                             :data-toggle "modal"
                             :data-target "#my-modal"}
    "編集"]
   [:button.btn.btn-danger {:type "button"} "削除"]])

(defn items-table [spec items]
  (let [fields (:fields spec)]
    `[:table.table.table-striped
      [:thead
       [:tr
        ~@(for [{:keys [title]} fields]
            [:th title])
        [:th "アクション"]]]
      ~(when items
         `[:tbody
           ~@(for [item items]
               `[:tr
                 ~@(for [{:keys [field]} fields]
                     [:td (get item field)])
                 ~[edit-buttons]])])]))

(defn edit-modal []
  [:div#my-modal.modal.fade {:role "dialog"}
   [:div.modal-dialog
    [:div.modal-content
     [:div.modal-header
      [:button.close {:type "button" :data-dismiss "modal" :aria-label "Close"}
       "&times;"]
      [:h4.modal-title "Modal title"]]
     [:div.modal-body "One fine body"]
     [:div.modal-footer
      [:button.btn.btn-default {:type "button" :data-dismiss "modal"}
       "Close"]
      [:button.btn.btn-primary {:type "button"} "Save changes"]]]]])

(defn app []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (when @spec
        [:div
         [:div.row
          [:div.col-md-1]
          [:div.col-md-10
           [:h1 (:title @spec)]
           [items-table @spec @items]]
          [:div.col-md-1]]
         [edit-modal]]))))

(defn ^:export main []
  (r/dispatch [:init "products"])
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
