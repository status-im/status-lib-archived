(ns status-im.protocol.state.delivery
  (:require [cljs-time.core :as t]
            [status-im.utils.logging :as log]
            [status-im.protocol.state.state :refer [state]])
  (:require-macros [status-im.utils.lang-macros :refer [condas->]]))

(defn pending-messages []
  (:pending-messages @state))

(defn set-pending-messages
  [messages]
  (swap! state assoc :pending-messages messages))

(defn add-pending-message
  ([message-id message {:keys [identities internal?] :as opts}]
   (swap! state assoc-in [:pending-messages message-id] (merge message {:identities  identities
                                                                        :internal?   internal?})))
  ([message-id message]
   (add-pending-message message-id message nil)))

(defn update-pending-message [message-id new-data]
  (swap! state (fn [state]
                 (if (get-in state [:pending-messages message-id])
                   (update-in state [:pending-messages message-id] merge new-data)
                   state))))

(defn update-pending-message-identities [id from]
  (swap! state update-in [:pending-messages]
         (fn [pending-messages]
           (condas-> pending-messages messages
                     (get-in messages [id :identities])         ;; test
                     (do
                       (log/info "Removing identity" from "from pending message" id)
                       (update-in messages [id :identities] disj from))

                     (empty? (get-in messages [id :identities])) ;; test
                     (do
                       (log/info "Removing message" id "from pending")
                       (dissoc messages id))))))

(defn remove-pending-message [message-id]
  (swap! state update-in [:pending-messages] dissoc message-id))

(defn internal? [message-id]
  (get-in @state [:pending-messages message-id :internal?]))

(defn inc-retry-count [message-id]
  (swap! state (fn [state]
                 (if (get-in state [:pending-messages message-id])
                   (update-in state [:pending-messages message-id :retry-count] inc)
                   state))))

(defn get-pending-message [message-id]
  (get-in @state [:pending-messages message-id]))
