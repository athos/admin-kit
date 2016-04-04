(ns example.core
  (:refer-clojure :exclude [find])
  (:require [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [ring.util.response :refer [response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [context]]
            [clj-time
             [coerce :as coerce]
             [format :as format]]
            [lustered
             [adapter :as adapter]
             [handler :as lustered]])
  (:import [java.util Date]))

(defn fresh-id [a]
  (-> (alter-meta! a update :fresh-id (fnil inc 0))
      :fresh-id))

(defn add! [a {:keys [name furigana price]}]
  (let [now (Date.)
        id  (fresh-id a)
        new-item {:id id
                  :name name
                  :furigana furigana
                  :price price
                  :created-at now
                  :modified-at now}]
    (swap! a assoc id new-item)
    new-item))

(defrecord OnMemoryDBAdapter [products]
  adapter/Creatable
  (create [this {:keys [price] :as product}]
    (let [price (Long/parseLong price)]
      (add! products (assoc product :price price))))

  adapter/Readable
  (read [this {:keys [id]}]
    (if id
      (filter #(= (:id %) id) (vals @products))
      (sort-by :id (vals @products))))

  adapter/Updatable
  (update [this {:keys [id price] :as product}]
    (let [id (Long/parseLong id)
        price (Long/parseLong price)
        new-fields (-> product
                       (select-keys [:name :furigana])
                       (assoc :price price :modified-at (Date.)))]
      (-> (swap! products update id merge new-fields)
          (get id))))

  adapter/Deletable
  (delete [this {:keys [id]}]
    (let [id (Long/parseLong id)]
      (swap! products dissoc id)
      id)))

(def adapter
  (->OnMemoryDBAdapter
   (doto (atom {})
     (add! {:name "ノート", :furigana "ノート", :price 250})
     (add! {:name "鉛筆", :furigana "エンピツ", :price 120})
     (add! { :name "消しゴム", :furigana "ケシゴム", :price 80}))))

(defn date-formatter [date]
  (format/unparse (format/formatter "yyyy/MM/dd") (coerce/from-date date)))

(def admin-site-spec
  [[:products
    {:adapter adapter
     :spec {:title "商品"
            :fields [{:field :id
                      :label "ID"
                      :format #(format "%03d" %)}
                     {:field :name
                      :label "名前"
                      :type :text}
                     {:field :furigana
                      :label "フリガナ"
                      :type :text}
                     {:field :price
                      :label "値段"
                      :type :text}
                     {:field :created-at
                      :label "登録日"
                      :format date-formatter}
                     {:field :modified-at
                      :label "最終更新日"
                      :format date-formatter}]}}]])

(def app
  (context "/admin" []
    (lustered/make-admin-site-handler admin-site-spec)))

(defn start-server []
  (let [port (Long/parseLong (get env :port "8080"))]
    (run-jetty app {:port port :join? false})))

(defstate server
  :start (start-server)
  :stop (.stop server))
