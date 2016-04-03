(ns lustered.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as r]))

(r/register-sub
 :spec
 (fn [db _]
   (reaction (:spec @db))))

(r/register-sub
 :items
 (fn [db _]
   (reaction (:items @db))))

(r/register-sub
 :editing-item
 (fn [db _]
   (reaction (:editing-item @db))))

(r/register-sub
 :modal-shown?
 (fn [db _]
   (reaction (:modal-shown? @db))))
