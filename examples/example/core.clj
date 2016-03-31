(ns example.core
  (:require [mount.core :refer [defstate]]
            [environ.core :refer [env]]
            [ring.util.response :refer [response]]
            [ring.adapter.jetty :refer [run-jetty]]
            [superficial.handler :as sup]))

(def sample-products
  (let [now (java.util.Date.)
        base {:created-at now, :modified-at now}]
    (->> [{:id 1, :name "ノート", :furigana "ノート", :price 250}
          {:id 2, :name "鉛筆", :furigana "エンピツ", :price 120}
          {:id 3, :name "消しゴム", :furigana "ケシゴム", :price 80}]
         (map (partial merge base)))))

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
             :title "登録日"}
            {:field :modified-at
             :title "最終更新日"}]
   :on-read (fn [& args] sample-products)})

(def app (sup/make-admin-page-handler "/admin" admin-page-spec))

(defn start-server []
  (let [port (Long/parseLong (get env :port "8080"))]
    (run-jetty app {:port port :join? false})))

(defstate server
  :start (start-server)
  :stop (.stop server))
