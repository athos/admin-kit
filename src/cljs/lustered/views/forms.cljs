(ns lustered.views.forms
  (:require [reagent.core :as reagent]
            [cljsjs.react-bootstrap]))

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
        value (str (or value (first (keys values))))
        on-change (fn [e] (updater (.. e -target -value)))]
    (updater value)
    [:div.form-group
     [:label.control-label.col-xs-3 (:label field)]
     [:div.col-xs-9
      (for [[val label] (:values field)
            :let [val (str val)]]
        ^{:key val}
        [:label.radio-inline
         [:input {:type :radio
                  :name field-name
                  :value val
                  :checked (= val value)
                  :on-change on-change}]
         label])]]))

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
