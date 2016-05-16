(ns lustered.utils
  (:require [goog.Uri :as uri]))

(defn page-state->uri [base-path {:keys [page-name page-no order desc?]}]
  (let [page-name (if (string? page-name) page-name (name page-name))
        uri (uri/parse (str base-path "/pages/" page-name))]
    (cond-> (.getQueryData uri)
      page-no (.add "page" page-no)
      order (.add "order" (name order))
      desc? (.add "desc" "true"))
    (.toString uri)))

(defn uri->page-state [uri]
  (let [uri (uri/parse (str uri))
        path (.getPath uri)]
    (when-let [[match? base-path page-name]
               (re-matches #"^(.+)/pages(?:/([^/]+)/?)?$" path)]
      (let [queries (.getQueryData uri)
            page-no (some-> (.get queries "page") long)
            order (some-> (.get queries "order") keyword)
            desc? (boolean (.get queries "desc"))]
        [base-path {:page-name page-name :page-no page-no
                    :order order :desc? desc?}]))))
