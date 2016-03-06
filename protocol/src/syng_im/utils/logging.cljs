(ns syng-im.utils.logging
  (:require [cljs-time.core :as t]
            [cljs-time.format :as tf]))

(defn timestamp []
  (tf/unparse (:hour-minute-second-fraction tf/formatters) (t/now)))

(defn info [& args]
  (let [args (cons (timestamp) args)]
    (.apply (.-log js/console) js/console (into-array args))))

(defn warn [& args]
  (let [args (cons (timestamp) args)]
    (.apply (.-warn js/console) js/console (into-array args))))

(defn error [& args]
  (let [args (cons (timestamp) args)]
    (.apply (.-error js/console) js/console (into-array args))))


(comment

  )