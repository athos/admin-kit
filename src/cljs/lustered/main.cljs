(ns lustered.main
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [lustered.utils :as utils]
            [lustered.handlers :as handlers]
            [lustered.views.core :as views]))

;;
;; Entry point
;;

(defn ^:export main []
  (when-let [[base-path page-state] (utils/uri->page-state (.-location js/window))]
    (handlers/init base-path #(handlers/page-init page-state)))
  (reagent/render [views/app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(.addEventListener js/window "popstate"
                   (fn [e]
                     (when-let [page-state (.-state e)]
                       (-> page-state
                           (js->clj :keywordize-keys true)
                           handlers/page-init))))
