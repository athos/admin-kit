(ns example.core
  (:refer-clojure :exclude [find])
  (:require [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [ring.util.response :refer [response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [context]]
            [validateur.validation :as v]
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
        new-item (merge item {:_id id :created-at now :modified-at now})]
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
      (read [this {:keys [_id]}]
        (if _id
          (filter #(= (:_id %) _id) (vals @categories))
          (sort-by :_id (vals @categories))))

      adapter/Update
      (update [this {:keys [_id] :as category}]
        (swap! categories update _id merge (select-keys category [:name])))

      adapter/Delete
      (delete [this {:keys [_id]}]
        (swap! categories dissoc _id)))))

(defn category-name [id]
  (-> (adapter/read categories-adapter {:_id id})
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
                            :benefits (= (rand-int 2) 0)}))
        products (let [products (atom {})]
                   (doseq [product (map random-product (range 25))]
                     (add! products product))
                   products)]
    (reify
      adapter/Create
      (create [this {:keys [price category benefits] :as product}]
        (let [price (Long/parseLong price)
              category (Long/parseLong category)]
          (add! products
                (merge product
                       {:price price
                        :category category
                        :benefits benefits}))))

      adapter/Read
      (read [this {:keys [_id _offset _limit _order]}]
        (cond->> (sort-by :_id (vals @products))
          _order (sort-by (:field _order)
                          (if (:desc? _order)
                            (comp - compare)
                            compare))
          _offset (drop _offset)
          _limit (take _limit)))

      adapter/Update
      (update [this {:keys [_id price category benefits] :as product}]
        (let [price (Long/parseLong price)
              category (Long/parseLong category)
              new-fields (-> product
                             (select-keys [:name :furigana])
                             (assoc :price price
                                    :category category
                                    :benefits benefits
                                    :modified-at (Date.)))]
          (-> (swap! products update _id merge new-fields)
              (get _id))))

      adapter/Delete
      (delete [this {:keys [_id]}]
        (swap! products dissoc _id)
        _id)

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
        (sort-by :_id (vals @sets)))

      adapter/Update
      (update [this {:keys [_id] :as product-set}]
        (swap! sets update _id merge (select-keys product-set
                                                  [:name :products])))

      adapter/Delete
      (delete [this {:keys [_id]}]
        (swap! sets dissoc _id)))))

(defn date-formatter [date]
  (format/unparse (format/formatter "yyyy/MM/dd") (coerce/from-date date)))

(def admin-site-spec
  [[:products
    {:adapter products-adapter
     :validator (v/validation-set
                 (v/presence-of :name :message "名前を入力して下さい。")
                 (v/presence-of :furigana :message "フリガナを入力して下さい。")
                 (v/format-of :price
                              :format #"\d+"
                              :message "値段は整数で入力して下さい。"
                              :blank-message "値段を入力して下さい。"))
     :spec {:title "商品"
            :fields [{:name :_id
                      :label "ID"
                      :format #(format "%03d" %)
                      :detail? true}
                     {:name :name
                      :label "名前"
                      :type :text}
                     {:name :furigana
                      :label "フリガナ"
                      :type :text
                      :sortable? true}
                     {:name :price
                      :label "値段"
                      :type :text
                      :sortable? true}
                     {:name :category
                      :label "カテゴリー"
                      :type :select
                      :values #(->> (adapter/read categories-adapter {})
                                    (map (fn [{:keys [_id name]}] [_id name])))}
                     {:name :benefits
                      :label "特典"
                      :type :checkbox
                      :values {true "あり" false "なし"}
                      :default true}
                     {:name :created-at
                      :label "登録日"
                      :format date-formatter
                      :detail? true}
                     {:name :modified-at
                      :label "最終更新日"
                      :format date-formatter
                      :detail? true}]}}]
   [:product-sets
    {:adapter product-sets-adapter
     :spec {:title "商品セット"
            :fields [{:name :_id
                      :label "ID"
                      :format #(format "%02d" %)
                      :detail? true}
                     {:name :name
                      :label "名称"
                      :type :text}
                     {:name :products
                      :label "対象商品"
                      :type :multi-checkbox
                      :values #(->> (adapter/read products-adapter {})
                                    (map (fn [{:keys [_id name]}] [_id name])))
                      :detail? true}]}}]
   [:categories
    {:adapter categories-adapter
     :spec {:title "商品カテゴリー"
            :fields [{:name :_id
                      :label "ID"
                      :format #(format "%02d" %)}
                     {:name :name
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
