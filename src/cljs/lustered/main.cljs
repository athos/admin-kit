(ns lustered.main
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [lustered.handlers :as handlers]
            [lustered.views.core :as views]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;;
;; Entry point
;;

(defn ^:export main []
  (when-let [[match? base-path page-name]
             (re-matches #"^(.+/pages)(?:/([^/]+)/?)?$"
                         (.. js/window -location -pathname))]
    (handlers/init base-path #(handlers/page-init page-name)))
  (reagent/render [views/app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(.addEventListener js/window "popstate"
                   (fn [e]
                     (when-let [page-name (.-state e)]
                       (handlers/page-init page-name))))

(defn on-js-reload [])
