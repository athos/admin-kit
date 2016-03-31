(ns lustered.core
  (:require [reagent.core :as reagent]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(defn app []
  [:h1 "Welcome to admin page!!"])

(defn ^:export main []
  (reagent/render [app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
