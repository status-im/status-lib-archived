(ns status-im.protocol.state.state
  (:require [cljs-time.core :as t]))

(def state (atom {:pending-messages {}
                  :filters          {}
                  :delivery-queue   #queue []
                  :external-handler nil
                  :account          nil
                  :connection       nil
                  :storage          nil}))

(defn add-filter [topics filter]
  (swap! state assoc-in [:filters topics] filter))

(defn get-filter [topics]
  (get-in @state [:filters topics]))

(defn remove-filter [topics]
  (swap! state update-in [:filters] dissoc topics))

(defn remove-all-filters []
  (swap! state :filters {}))

(defn get-all-filters []
  (vals (@state :filters)))

(defn set-storage [storage]
  (swap! state assoc :storage storage))

(defn set-handler [handler]
  (swap! state assoc :external-handler handler))

(defn set-account [identity]
  (swap! state assoc :account identity))

(defn set-connection [connection]
  (swap! state assoc :connection connection))

(defn connection []
  (:connection @state))

(defn my-account []
  (:account @state))

(defn my-identity []
  (get-in @state [:account :public-key]))

(defn external-handler []
  (:external-handler @state))

(defn storage []
  (:storage @state))
