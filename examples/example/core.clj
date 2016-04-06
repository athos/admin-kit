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

(defn add! [a item]
  (let [now (Date.)
        id  (fresh-id a)
        new-item (merge item {:id id :created-at now :modified-at now})]
    (swap! a assoc id new-item)
    new-item))

(def products-adapter
  (let [products (doto (atom {})
                   (add! {:name "ノート", :furigana "ノート", :price 250})
                   (add! {:name "鉛筆", :furigana "エンピツ", :price 120})
                   (add! {:name "消しゴム", :furigana "ケシゴム", :price 80}))]
    (reify
      adapter/Create
      (create [this {:keys [price] :as product}]
        (let [price (Long/parseLong price)]
          (add! products (assoc product :price price))))

      adapter/Read
      (read [this {:keys [id]}]
        (sort-by :id (vals @products)))

      adapter/Update
      (update [this {:keys [id price] :as product}]
        (let [id (Long/parseLong id)
              price (Long/parseLong price)
              new-fields (-> product
                             (select-keys [:name :furigana])
                             (assoc :price price :modified-at (Date.)))]
          (-> (swap! products update id merge new-fields)
              (get id))))

      adapter/Delete
      (delete [this {:keys [id]}]
        (let [id (Long/parseLong id)]
          (swap! products dissoc id)
          id)))))

(def categories-adapter
  (let [categories (doto (atom {})
                     (add! {:name "衣服"})
                     (add! {:name "食料品"})
                     (add! {:name "文房具"}))]
    (reify
      adapter/Create
      (create [this category]
        (add! categories (select-keys category [:name])))

      adapter/Read
      (read [this _]
        (sort-by :id (vals @categories)))

      adapter/Update
      (update [this {:keys [id] :as category}]
        (prn category)
        (let [id (Long/parseLong id)]
          (swap! categories update id merge (select-keys category [:name]))))

      adapter/Delete
      (delete [this {:keys [id]}]
        (let [id (Long/parseLong id)]
          (swap! categories dissoc id))))))

(defn date-formatter [date]
  (format/unparse (format/formatter "yyyy/MM/dd") (coerce/from-date date)))

(def admin-site-spec
  [[:products
    {:adapter products-adapter
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
                      :format date-formatter}]}}]
   [:categories
    {:adapter categories-adapter
     :spec {:title "商品カテゴリー"
            :fields [{:field :id
                      :label "ID"
                      :format #(format "%02d" %)}
                     {:field :name
                      :label "名称"
                      :type :text}]}}]])

(def app
  (context "/admin" []
    (lustered/make-admin-site-handler admin-site-spec)))

(defn start-server []
  (let [port (Long/parseLong (get env :port "8080"))]
    (run-jetty app {:port port :join? false})))

(defstate server
  :start (start-server)
  :stop (.stop server))
