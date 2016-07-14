(ns admin-kit.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [admin-kit.utils :as utils]
            [admin-kit.handlers :as handlers]
            [admin-kit.views.core :as views]))

(defn ^:export init []
  (when-let [[base-path page-state] (utils/uri->page-state (.-location js/window))]
    (handlers/init base-path #(handlers/page-init page-state)))
  (reagent/render [views/app] (.getElementById js/document "app")))

(defn ^:export start []
  (.addEventListener js/window "load" init)
  (.addEventListener js/window "popstate"
                     (fn [e]
                       (when-let [page-state (.-state e)]
                         (-> page-state
                             (js->clj :keywordize-keys true)
                             (update :order keyword)
                             handlers/page-init)))))
