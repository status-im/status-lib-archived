(ns status-im.utils.random
  (:require [cljsjs.chance]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]))

(defn timestamp []
  (.getTime (js/Date.)))

(defn id []
  (str (timestamp) "-" (.guid js/chance)))

(comment
  (id)
  )