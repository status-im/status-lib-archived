(ns syng-im.utils.logging
  (:require [cljs-time.core :as t]
            [cljs-time.format :as tf]))

(defn timestamp []
  (tf/unparse (:hour-minute-second-fraction tf/formatters) (t/now)))

(defn to-js [args]
  (->> (cons (timestamp) args)
       (mapv clj->js)
       (into-array)))

(defn debug [& args]
  (.apply (.-log js/console) js/console (to-js args)))

(defn info [& args]
  (.apply (.-log js/console) js/console (to-js args)))

(defn warn [& args]
  (.apply (.-warn js/console) js/console (to-js args)))

(defn error [& args]
  (.apply (.-error js/console) js/console (to-js args)))


(comment

  )