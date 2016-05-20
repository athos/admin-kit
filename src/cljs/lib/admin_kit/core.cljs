(ns admin-kit.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [admin-kit.utils :as utils]
            [admin-kit.handlers :as handlers]
            [admin-kit.views.core :as views]))

(defn start []
  (when-let [[base-path page-state] (utils/uri->page-state (.-location js/window))]
    (handlers/init base-path #(handlers/page-init page-state)))
  (reagent/render [views/app] (.getElementById js/document "app")))
