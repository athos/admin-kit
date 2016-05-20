(ns admin-kit.main
  (:require [admin-kit.core :as admin]
            [admin-kit.handlers :as handlers]))

(.addEventListener js/window "load" admin/start)

(.addEventListener js/window "popstate"
                   (fn [e]
                     (when-let [page-state (.-state e)]
                       (-> page-state
                           (js->clj :keywordize-keys true)
                           (update :order keyword)
                           handlers/page-init))))
