(ns lustered.utils
  (:require [goog.Uri :as uri]))

(defn page-state->uri [base-path {:keys [page-name offset limit]}]
  (let [page-name (if (string? page-name) page-name (name page-name))
        uri (uri/parse (str base-path "/" page-name))]
    (cond-> (.getQueryData uri)
      offset (.add "offset" offset)
      limit (.add "limit" limit))
    (.toString uri)))

(defn uri->page-state [uri]
  (let [uri (uri/parse (str uri))
        path (.getPath uri)]
    (when-let [[match? base-path page-name]
               (re-matches #"^(.+/pages)(?:/([^/]+)/?)?$" path)]
      (let [queries (.getQueryData uri)
            offset (.get queries "offset")
            limit (.get queries "limit")]
        [base-path {:page-name page-name :offset offset :limit limit}]))))
