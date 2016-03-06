(ns cljs-tests.protocol.state.state
  (:require [cljs-time.core :as t]))

(def state (atom {:pending-messages {}
                  :filters          {}
                  :delivery-queue   #queue []
                  :handler          nil
                  :identity         nil
                  :connection       nil}))

(defn add-filter [topics filter]
  (swap! state assoc-in [:filters topics] filter))

(defn set-handler [handler]
  (swap! state assoc :handler handler))

(defn set-identity [identity]
  (swap! state assoc :identity identity))

(defn set-connection [connection]
  (swap! state assoc :connection connection))

(defn connection []
  (:connection @state))

(defn my-identity []
  (:identity @state))

(defn handler []
  (:handler @state))
