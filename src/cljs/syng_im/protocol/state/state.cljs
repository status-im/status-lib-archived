(ns syng-im.protocol.state.state
  (:require [cljs-time.core :as t]))

(def state (atom {:pending-messages {}
                  :filters          {}
                  :delivery-queue   #queue []
                  :external-handler nil
                  ;; todo this is not too smart. This works like singleton and
                  ;; doesn't allow to test library in one process, furthermore
                  ;; when user will have more then one identity he will
                  ;; explicitly choose which identity to use in api calls.
                  :identity         nil
                  :connection       nil
                  :storage          nil}))

(defn add-filter [topics filter]
  (swap! state assoc-in [:filters topics] filter))

(defn get-filter [topics]
  (get-in @state [:filters topics]))

(defn remove-filter [topics]
  (swap! state update-in [:filters] dissoc topics))

(defn set-storage [storage]
  (swap! state assoc :storage storage))

(defn set-handler [handler]
  (swap! state assoc :external-handler handler))

(defn set-identity [identity]
  (swap! state assoc :identity identity))

(defn set-connection [connection]
  (swap! state assoc :connection connection))

(defn connection []
  (:connection @state))

(defn my-identity []
  (:identity @state))

(defn external-handler []
  (:external-handler @state))

(defn storage []
  (:storage @state))
