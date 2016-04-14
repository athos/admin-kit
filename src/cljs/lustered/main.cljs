(ns lustered.main
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [lustered.handlers :as handlers]
            [lustered.views.core :as views]
            [goog.Uri :as uri]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

(defn parse-uri [location]
  (let [uri (uri/parse (.toString location))
        path (.getPath uri)]
    (when-let [[match? base-path page-name]
               (re-matches #"^(.+/pages)(?:/([^/]+)/?)?$" path)]
      (let [queries (.getQueryData uri)
            offset (.get queries "offset")
            limit (.get queries "limit")]
        [base-path {:page-name page-name :offset offset :limit limit}]))))

;;
;; Entry point
;;

(defn ^:export main []
  (let [[base-path page-state] (parse-uri (.-location js/window))]
    (handlers/init base-path #(handlers/page-init page-state)))
  (reagent/render [views/app] (.getElementById js/document "app")))

(.addEventListener js/window "load" main)

(.addEventListener js/window "popstate"
                   (fn [e]
                     (when-let [page-state (.-state e)]
                       (-> page-state
                           (js->clj :keywordize-keys true)
                           handlers/page-init))))

(defn on-js-reload [])
