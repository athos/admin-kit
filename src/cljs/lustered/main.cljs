(ns lustered.main
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [lustered.handlers]
            [lustered.views :as views]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;;
;; Entry point
;;

(defn ^:export main []
  (let [page-name (re-find #"[^/]+$" (.. js/window -location -pathname))]
    (r/dispatch [:init page-name]))
  (reagent/render [views/app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload [])
