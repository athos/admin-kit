(ns lustered.main
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [lustered.handlers]
            [lustered.views :as views]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(defn dispatch [path]
  (let [[match? page-name] (re-find #"/pages(?:/([^/]+)/?)?$" path)]
    (if match?
      (if page-name
        (r/dispatch [:init page-name])))))

(.addEventListener js/window "popstate"
                   (fn [e]
                     (when-let [page-name (.-state e)]
                       (r/dispatch [:init page-name]))))

;;
;; Entry point
;;

(defn ^:export main []
  (dispatch (.. js/window -location -pathname))
  (reagent/render [views/app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(defn on-js-reload [])
