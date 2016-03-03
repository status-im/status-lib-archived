(ns cljs-tests.utils.logging)

(defn info [& args]
  (.apply (.-log js/console) js/console (into-array args)))

(defn warn [& args]
  (.apply (.-warn js/console) js/console (into-array args)))

(defn error [& args]
  (.apply (.-error js/console) js/console (into-array args)))

