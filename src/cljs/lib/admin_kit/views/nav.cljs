(ns admin-kit.views.nav
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [admin-kit.handlers :as handlers]
            [admin-kit.subs]
            [admin-kit.utils :as utils]))

(def ListGroup
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroup)))
(def ListGroupItem
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroupItem)))

(defn pages-navigation []
  (let [base-path (r/subscribe [:base-path])
        pages (r/subscribe [:pages])
        dispatch (fn [page-spec]
                   (fn [e]
                     (.preventDefault e)
                     (handlers/move-to-page page-spec)))]
    (fn []
      [ListGroup
       (when @pages
         (->> (for [page-spec @pages
                    :let [page-name (name (:name page-spec))
                          page-state {:page-name page-name}]]
                ^{:key page-name}
                [ListGroupItem {:href (utils/page-state->uri @base-path
                                                             page-state)
                                :on-click (dispatch page-spec)}
                 (:title page-spec)])
              doall))])))
