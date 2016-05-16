(ns lustered.views.utils
  (:require [lustered.handlers :as handlers]))

(defn open-modal [index item]
  (handlers/save :editing-item {:index index :item item})
  (handlers/save :modal-shown? true)
  (handlers/save :edit-errors nil)
  (handlers/save :validation-errors nil))

(defn close-modal []
  (handlers/save :editing-item nil)
  (handlers/save :modal-shown? false)
  (handlers/save :edit-errors nil)
  (handlers/save :validation-errors nil))

(defn rendered-value [item field-name values]
  (or (get item (keyword "_rendered" (name field-name)))
      (let [field-value (get item field-name)]
        (if values
          (get (into {} values) field-value)
          field-value))))
