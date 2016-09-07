(ns status-im.protocol.state.delivery
  (:require [cljs-time.core :as t]
            [status-im.utils.logging :as log]
            [status-im.protocol.state.state :refer [state]]
            [status-im.protocol.user-handler :refer [invoke-user-handler]])
  (:require-macros [status-im.utils.lang-macros :refer [condas->]]))

(defn pending-messages []
  (:pending-messages @state))

(defn set-pending-messages
  [messages]
  (swap! state assoc :pending-messages messages))

(defn upsert-pending-message
  ([message]
   (upsert-pending-message message nil))
  ([{:keys [message-id] :as message} {:keys [identities internal?]}]
   (let [message (merge message {:identities  identities
                                 :internal?   internal?})]
     (invoke-user-handler :pending-message-upsert {:message message})
     (swap! state assoc-in [:pending-messages message-id] message))))

(defn update-pending-message-identities [id from]
  (swap! state update-in [:pending-messages]
         (fn [pending-messages]
           (condas-> pending-messages messages
                     (get-in messages [id :identities])         ;; test
                     (do
                       (log/info "Removing identity" from "from pending message" id)
                       (invoke-user-handler :pending-message-upsert {:message (get messages id)})
                       (update-in messages [id :identities] disj from))

                     (empty? (get-in messages [id :identities])) ;; test
                     (do
                       (log/info "Removing message" id "from pending")
                       (invoke-user-handler :pending-message-remove {:message-id id})
                       (dissoc messages id))))))

(defn remove-pending-message [message-id]
  (invoke-user-handler :pending-message-remove {:message-id message-id})
  (swap! state update-in [:pending-messages] dissoc message-id))

(defn internal? [message-id]
  (get-in @state [:pending-messages message-id :internal?]))

(defn get-pending-message [message-id]
  (get-in @state [:pending-messages message-id]))
