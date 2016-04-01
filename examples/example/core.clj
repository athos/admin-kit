(ns example.core
  (:refer-clojure :exclude [find])
  (:require [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [ring.util.response :refer [response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [clj-time
             [coerce :as coerce]
             [format :as format]]
            [lustered.handler :as lustered])
  (:import [java.util Date]))

(defn inc-count!
  ([a] (inc-count! 1 a))
  ([n a]
   (alter-meta! a update :count (fnil #(+ % n) 0))
   a))

(defstate sample-products
  :start (let [now (Date.)
               base {:created-at now, :modified-at now}]
           (->> [{:id 1, :name "ノート", :furigana "ノート", :price 250}
                 {:id 2, :name "鉛筆", :furigana "エンピツ", :price 120}
                 {:id 3, :name "消しゴム", :furigana "ケシゴム", :price 80}]
                (mapv (partial merge base))
                atom
                (inc-count! 3))))

(defn create! [{:keys [name furigana price]}]
  (inc-count! sample-products)
  (let [now (Date.)
        new-item {:id (:count (meta sample-products))
                  :name name
                  :furigana furigana
                  :price price
                  :created-at now
                  :modified-at now}]
    (swap! sample-products conj new-item)
    new-item))

(defn find [{:keys [id]}]
  (if id
    (filterv #(= (:id %) id) @sample-products)
    @sample-products))

(defn date-formatter [date]
  (format/unparse (format/formatter "yyyy/MM/dd") (coerce/from-date date)))

(def admin-page-spec
  {:name :products
   :title "商品"
   :fields [{:field :id
             :title "ID"
             :format #(format "%03d" %)}
            {:field :name
             :title "名前"
             :type :text}
            {:field :furigana
             :title "フリガナ"
             :type :text}
            {:field :price
             :title "値段"
             :type :text}
            {:field :created-at
             :title "登録日"
             :format date-formatter}
            {:field :modified-at
             :title "最終更新日"
             :format date-formatter}]
   :on-create create!
   :on-read find})

(def app (lustered/make-admin-page-handler "/admin" admin-page-spec))

(defn start-server []
  (let [port (Long/parseLong (get env :port "8080"))]
    (run-jetty app {:port port :join? false})))

(defstate server
  :start (start-server)
  :stop (.stop server))
