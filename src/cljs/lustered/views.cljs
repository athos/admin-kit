(ns lustered.views
  (:require [reagent.core :as reagent]
            [re-frame.core :as r]
            [cljsjs.react-bootstrap]
            [lustered.handlers :as handlers]
            [lustered.subs]))

;;
;; Utilities
;;

(defn open-modal [index item]
  (handlers/save :editing-item {:index index :item item})
  (handlers/save :modal-shown? true))

(defn close-modal []
  (handlers/save :editing-item nil)
  (handlers/save :modal-shown? false))

;;
;; Components
;;

(def ListGroup
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroup)))
(def ListGroupItem
  (reagent/adapt-react-class (.. js/ReactBootstrap -ListGroupItem)))

(defn pages-navigation []
  (let [base-path "/admin/pages"
        page-specs [{:name :products :title "商品"}
                    {:name :categories :title "商品カテゴリー"}]
        dispatch (fn [path page-name]
                   (fn [e]
                     (.preventDefault e)
                     (.pushState js/history page-name nil path)
                     (handlers/page-init page-name)))]
    (fn []
      [ListGroup
       (for [{page-name :name page-title :title} page-specs
             :let [page-name (name page-name)
                   path (str base-path "/" page-name)]]
         ^{:key page-name}
         [ListGroupItem {:href path :on-click (dispatch path page-name)}
          page-title])])))

(defn edit-buttons [index item]
  (letfn [(on-edit [] (open-modal index item))
          (on-delete []
            (when (js/confirm "Are you sure you want to delete the item?")
              (r/dispatch [:request-delete-item item])))]
    [:td
     [:a {:href "#" :on-click on-edit}
      [:span.fa-stack.fa-lg
       [:i.fa.fa-circle.fa-stack-2x.text-primary]
       [:i.fa.fa-pencil.fa-stack-1x.fa-inverse]]]
     [:a {:href "#" :on-click on-delete}
      [:span.fa-stack.fa-lg
       [:i.fa.fa-circle.fa-stack-2x.text-danger]
       [:i.fa.fa-trash.fa-stack-1x.fa-inverse]]]]))

(defn rendered-value [item field-name values]
  (or (get item (keyword "_rendered" (name field-name)))
      (let [field-value (get item field-name)]
        (if values
          (get (into {} values) field-value)
          field-value))))

(defn items-table []
  (let [spec (r/subscribe [:spec])
        items (r/subscribe [:items])]
    (fn []
      (let [fields (:fields @spec)]
        [:table.table.table-striped
         [:thead
          [:tr
           (for [{:keys [field label]} fields]
             ^{:key field} [:th label])
           [:th]]]
         (when @items
           [:tbody
            (for [[index item] (map-indexed vector @items)]
              ^{:key index}
              [:tr
               (for [{:keys [field values]} fields]
                 (let [rendered (rendered-value item field values)]
                   ^{:key field} [:td rendered]))
               (edit-buttons index item)])])]))))

(defn add-new-button []
  (let [spec (r/subscribe [:spec])]
    (fn []
      (let [new-item (->> (for [{:keys [field default]} (:fields spec)
                                :when default]
                            [field default])
                          (into {}))]
        [:button.btn.btn-success.pull-right
         {:type :button :on-click #(open-modal nil new-item)}
         [:i.fa.fa-plus-square-o] " Add new"]))))

(def FormControlsStatic
  (reagent/adapt-react-class (.. js/ReactBootstrap -FormControls -Static)))
(def Input
  (reagent/adapt-react-class (.. js/ReactBootstrap -Input)))

(defmulti render-field (fn [field value rendered updater] (:type field)))
(defmethod render-field :default [field value rendered _]
  [FormControlsStatic
   {:label (:label field)
    :label-class-name "col-xs-3"
    :wrapper-class-name "col-xs-9"
    :value rendered}])

(defmethod render-field :text [field value _ updater]
  (let [{field-name :field field-label :label} field]
    [Input {:type :text
            :label field-label
            :label-class-name "col-xs-3"
            :wrapper-class-name "col-xs-9"
            :placeholder field-label
            :default-value value
            :on-change (fn [e] (updater (.. e -target -value)))}]))

(defmethod render-field :select [field value _ updater]
  (let [{field-name :field field-label :label} field
        values (:values field)
        value (or value (first (keys values)))]
    (updater value)
    [Input {:type :select
            :label field-label
            :label-class-name "col-xs-3"
            :wrapper-class-name "col-xs-9"
            :defaultValue value
            :on-change (fn [e]
                         (let [target (.-target e)]
                           (updater (-> (.-options target)
                                        (aget (.-selectedIndex target))
                                        .-value))))}
     (for [[val label] values]
       ^{:key val} [:option {:value val} label])]))

(defmethod render-field :radio [field value _ updater]
  (let [{field-name :field field-label :label} field
        values (:values field)
        value (or value (first (keys values)))
        on-change (fn [e] (updater (.. e -target -value)))]
    (updater value)
    [:div.form-group
     [:label.control-label.col-xs-3 (:label field)]
     [:div.col-xs-9
      (for [[val label] (:values field)
            :let [id (str (name field-name) val)]]
        ^{:key val}
        [:div.radio-inline
         [:input.form-control (cond-> {:type :radio
                                       :id id
                                       :name field-name
                                       :value val
                                       :on-change on-change}
                                (= val value) (merge {:checked true}))]
         [:label {:for id} label]])]]))

(defmethod render-field :checkbox [field value _ updater]
  (let [{field-name :field field-label :label} field
        value (boolean value)
        on-change (fn [e] (updater (.. e -target -checked)))]
    (updater value)
    [:div.form-group
     [:label.control-label.col-xs-3 field-label]
     [:div.col-xs-9
      [:label.checkbox-inline
       [:input {:type :checkbox
                :checked value
                :on-change on-change}]
       (get (into {} (:values field)) true)]]]))

(defn modal-form [fields item]
  [:form.form-horizontal
   (for [{field-name :field :as field} fields]
     (letfn [(updater [val]
               (r/dispatch [:edit-item-field field-name val]))]
       (with-meta
         (render-field field
                       (get item field-name)
                       (rendered-value item field-name (:values field))
                       updater)
         {:key (name field-name)})))])

(defn modal-submit-button [editing-item]
  (letfn [(on-submit [_]
            (let [{:keys [index item]} @editing-item]
              (if index
                (r/dispatch [:request-update-item index item #(close-modal)])
                (r/dispatch [:request-create-item item #(close-modal)]))))]
    [:button.btn.btn-primary {:type "button" :on-click on-submit}
     "Save"]))

(def Modal
  (reagent/adapt-react-class (.. js/ReactBootstrap -Modal)))
(def ModalHeader
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalHeader)))
(def ModalTitle
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalTitle)))
(def ModalBody
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalBody)))
(def ModalFooter
  (reagent/adapt-react-class (.. js/ReactBootstrap -ModalFooter)))

(defn edit-modal []
  (let [spec (r/subscribe [:spec])
        modal-shown? (r/subscribe [:modal-shown?])
        editing-item (r/subscribe [:editing-item])]
    (fn []
      [Modal {:show @modal-shown? :on-hide close-modal}
       [ModalHeader {:close-button true}
        [ModalTitle (:title @spec)]]
       (when-let [{:keys [item]} @editing-item]
         [ModalBody (modal-form (:fields @spec) item)])
       [ModalFooter
        [:button.btn.btn-default {:type "button" :on-click close-modal}
         "Close"]
        [modal-submit-button editing-item]]])))

(defn app []
  (let [spec (r/subscribe [:spec])]
    (fn []
      (when @spec
        [:div
         [:div.row
          [:div.col-md-3
           [pages-navigation]]
          [:div.col-md-9
           [:h1 (:title @spec)]
           [items-table]
           [add-new-button]]]
         [edit-modal]]))))
