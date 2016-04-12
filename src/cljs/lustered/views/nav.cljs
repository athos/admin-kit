(ns lustered.views.nav
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]))

(def ListGroup
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroup)))
(def ListGroupItem
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroupItem)))

(defn pages-navigation []
  (let [base-path (r/subscribe [:base-path])
        pages (r/subscribe [:pages])
        dispatch (fn [path page-name]
                   (fn [e]
                     (.preventDefault e)
                     (.pushState js/history page-name nil path)
                     (handlers/page-init page-name)))]
    (fn []
      [ListGroup
       (when @pages
         (->> (for [{page-name :name page-title :title} @pages
                    :let [page-name (name page-name)
                          path (str @base-path "/" page-name)]]
                ^{:key page-name}
                [ListGroupItem {:href path :on-click (dispatch path page-name)}
                 page-title])
              doall))])))
