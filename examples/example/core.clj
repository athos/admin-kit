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
      (read [this {:keys [id]}]
        (if id
          (let [id (Long/parseLong id)]
            (filter #(= (:id %) id) (vals @categories)))
          (sort-by :id (vals @categories))))

      adapter/Update
      (update [this {:keys [id] :as category}]
        (let [id (Long/parseLong id)]
          (swap! categories update id merge (select-keys category [:name]))))

      adapter/Delete
      (delete [this {:keys [id]}]
        (let [id (Long/parseLong id)]
          (swap! categories dissoc id))))))

(defn category-name [id]
  (-> (adapter/read categories-adapter {:id (str id)})
      first
      :name))

(def products-adapter
  (let [random-product (fn [i]
                         (let [furiganas {"衣服" "イフク"
                                          "食料品" "ショクリョウヒン"
                                          "文房具" "ブンボウグ"}
                               category (-> (count furiganas)
                                            rand-int
                                            inc)
                               category-name (category-name category)]
                           {:name (str category-name i)
                            :furigana (str (get furiganas category-name) i)
                            :price (* 10 (rand-int 100))
                            :category category
                            :category-name category-name
                            :benefits (= (rand-int 2) 0)}))
        products (let [products (atom {})]
                   (doseq [product (map random-product (range 100))]
                     (add! products product))
                   products)]
    (reify
      adapter/Create
      (create [this {:keys [price category benefits] :as product}]
        (let [price (Long/parseLong price)
              category (Long/parseLong category)
              benefits (Boolean/valueOf benefits)]
          (add! products
                (merge product
                       {:price price
                        :category category
                        :category-name (category-name category)
                        :benefits benefits}))))

      adapter/Read
      (read [this {:keys [id _offset _limit]}]
        (cond->> (sort-by :id (vals @products))
          _offset (drop _offset)
          _limit (take _limit)))

      adapter/Update
      (update [this {:keys [id price category benefits] :as product}]
        (let [id (Long/parseLong id)
              price (Long/parseLong price)
              category (Long/parseLong category)
              benefits (Boolean/valueOf benefits)
              new-fields (-> product
                             (select-keys [:name :furigana])
                             (assoc :price price
                                    :category category
                                    :category-name (category-name category)
                                    :benefits benefits
                                    :modified-at (Date.)))]
          (-> (swap! products update id merge new-fields)
              (get id))))

      adapter/Delete
      (delete [this {:keys [id]}]
        (let [id (Long/parseLong id)]
          (swap! products dissoc id)
          id))

      adapter/Count
      (count [this params]
        (clojure.core/count @products)))))

(def product-sets-adapter
  (let [sets (atom {})]
    (reify
      adapter/Create
      (create [this params]
        (add! sets (select-keys params [:name :products])))

      adapter/Read
      (read [this params]
        (sort-by :id (vals @sets)))

      adapter/Update
      (update [this {:keys [id] :as product-set}]
        (let [id (Long/parseLong id)]
          (swap! sets update id merge (select-keys product-set
                                                   [:name :products]))))

      adapter/Delete
      (delete [this {:keys [id]}]
        (let [id (Long/parseLong id)]
          (swap! sets dissoc id))))))

(defn date-formatter [date]
  (format/unparse (format/formatter "yyyy/MM/dd") (coerce/from-date date)))

(def admin-site-spec
  [[:products
    {:adapter products-adapter
     :spec {:title "商品"
            :fields [{:field :id
                      :label "ID"
                      :format #(format "%03d" %)
                      :detail? true}
                     {:field :name
                      :label "名前"
                      :type :text}
                     {:field :furigana
                      :label "フリガナ"
                      :type :text}
                     {:field :price
                      :label "値段"
                      :type :text}
                     {:field :category
                      :label "カテゴリー"
                      :type :select
                      :render :category-name
                      :values #(->> (adapter/read categories-adapter {})
                                    (map (fn [{:keys [id name]}] [id name])))}
                     {:field :benefits
                      :label "特典"
                      :type :checkbox
                      :values {true "あり" false "なし"}
                      :default true}
                     {:field :created-at
                      :label "登録日"
                      :format date-formatter
                      :detail? true}
                     {:field :modified-at
                      :label "最終更新日"
                      :format date-formatter
                      :detail? true}]}}]
   [:product-sets
    {:adapter product-sets-adapter
     :spec {:title "商品セット"
            :fields [{:field :id
                      :label "ID"
                      :format #(format "%02d" %)
                      :detail? true}
                     {:field :name
                      :label "名称"
                      :type :text}
                     {:field :products
                      :label "対象商品"
                      :type :multi-select
                      :values #(->> (adapter/read products-adapter {})
                                    (map (fn [{:keys [id name]}] [id name])))
                      :detail? true}]}}]
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
