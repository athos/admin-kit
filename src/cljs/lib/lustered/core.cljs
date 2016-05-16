(ns lustered.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [lustered.utils :as utils]
            [lustered.handlers :as handlers]
            [lustered.views.core :as views]))

(defn start []
  (when-let [[base-path page-state] (utils/uri->page-state (.-location js/window))]
    (handlers/init base-path #(handlers/page-init page-state)))
  (reagent/render [views/app] (.getElementById js/document "app")))
