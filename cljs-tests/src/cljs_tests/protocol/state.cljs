(ns cljs-tests.protocol.state)

(def state (atom {:pending-messages {}
                  :filters          {}
                  :handler          nil
                  :identity         nil
                  :connection       nil}))

(defn add-pending-message [msg-id msg]
  (swap! state assoc-in [:pending-messages msg-id] msg))

(defn add-filter [topics filter]
  (swap! state assoc-in [:filters topics] filter))

(defn remove-pending-message [msg-id]
  (swap! state update-in [:pending-messages] dissoc msg-id))

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