(ns admin-kit.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as r]))

(r/register-sub
 :base-path
 (fn [db _]
   (reaction (:base-path @db))))

(r/register-sub
 :pages
 (fn [db _]
   (reaction (:pages @db))))

(r/register-sub
 :errors
 (fn [db _]
   (reaction (:errors @db))))

(r/register-sub
 :edit-errors
 (fn [db _]
   (reaction (:edit-errors @db))))

(r/register-sub
 :validation-errors
 (fn [db _]
   (reaction (:validation-errors @db))))

(r/register-sub
 :page-state
 (fn [db _]
   (reaction (:page-state @db))))

(r/register-sub
 :spec
 (fn [db _]
   (reaction (:spec @db))))

(r/register-sub
 :items
 (fn [db _]
   (reaction (:items @db))))

(r/register-sub
 :total-pages
 (fn [db _]
   (reaction (:total-pages @db))))

(r/register-sub
 :editing-item
 (fn [db _]
   (reaction (:editing-item @db))))

(r/register-sub
 :modal-shown?
 (fn [db _]
   (reaction (:modal-shown? @db))))
