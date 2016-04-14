(ns lustered.views.nav
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]
            [lustered.utils :as utils]))

(def ListGroup
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroup)))
(def ListGroupItem
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroupItem)))

(defn pages-navigation []
  (let [base-path (r/subscribe [:base-path])
        pages (r/subscribe [:pages])
        dispatch (fn [page-name]
                   (fn [e]
                     (.preventDefault e)
                     (handlers/move-to page-name)))]
    (fn []
      [ListGroup
       (when @pages
         (->> (for [{page-name :name page-title :title} @pages
                    :let [page-name (name page-name)
                          page-state {:page-name page-name}]]
                ^{:key page-name}
                [ListGroupItem {:href (utils/page-state->uri @base-path
                                                             page-state)
                                :on-click (dispatch page-name)}
                 page-title])
              doall))])))
