(ns status-im.protocol.delivery
  (:require [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as t]
            [cljs-time.coerce :refer [to-long
                                      from-long]]
            [status-im.utils.logging :as log]
            [status-im.protocol.state.state :refer [connection]]
            [status-im.protocol.web3 :refer [post-message]]
            [status-im.protocol.state.delivery :as state]
            [status-im.protocol.user-handler :refer [invoke-user-handler]]
            [status-im.protocol.defaults :refer [max-send-attempts
                                                 ack-wait-timeout
                                                 sending-retry-timeout
                                                 check-delivery-interval]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- needs-to-be-resent? [[_ {:keys [timestamp status]}]]
  (let [status (keyword status)]
    (or (= timestamp 0)
        (and (= status :sent)
             (t/before? (t/plus (from-long timestamp) ack-wait-timeout) (t/now)))
        (and (= status :sending)
             (t/before? (t/plus (from-long timestamp) sending-retry-timeout) (t/now))))))

(defn- delivery-pending-messages []
  (filter #(needs-to-be-resent? %) (state/pending-messages)))

(defn- post-message-callback [{:keys [message-id chat-id retry-count send-once] :as message}]
  (fn [error _]
    (if error
      (state/upsert-pending-message (assoc message :status :sending))
      (do
        (when chat-id
          (invoke-user-handler :message-sent {:message-id message-id
                                              :chat-id    chat-id}))
        (if send-once
          (state/remove-pending-message message-id)
          (state/upsert-pending-message (assoc message :status :sent
                                                       :retry-count (inc retry-count))))))))

(defn start-delivery-loop []
  (go (loop [_ (<! (timeout check-delivery-interval))]
        (doseq [[_ {:keys [message-id chat-id retry-count message] :as pending-message}] (delivery-pending-messages)]
          (log/info "Delivery-loop: Message" message-id "is pending, retry-count=" retry-count)
          (if (< retry-count max-send-attempts)
            (do
              (log/info "Delivery-loop: trying to send message" message-id)
              (let [pending-message (assoc pending-message :timestamp (to-long (t/now)))]
                (post-message (connection) message (post-message-callback pending-message))))
            (do
              (log/info "Delivery-loop: Retry-count for message" message-id "reached maximum")
              (state/upsert-pending-message (assoc message :status :failed))
              (when-not (state/internal? message-id)
                (invoke-user-handler :message-failed {:message-id message-id
                                                      :chat-id    chat-id})))))
        (recur (<! (timeout check-delivery-interval))))))