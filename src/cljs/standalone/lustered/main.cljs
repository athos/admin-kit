(ns lustered.main
  (:require [lustered.core :as lustered]
            [lustered.handlers :as handlers]))

(.addEventListener js/window "load" lustered/start)

(.addEventListener js/window "popstate"
                   (fn [e]
                     (when-let [page-state (.-state e)]
                       (-> page-state
                           (js->clj :keywordize-keys true)
                           handlers/page-init))))
