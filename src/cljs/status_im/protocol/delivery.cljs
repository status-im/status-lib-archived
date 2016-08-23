(ns status-im.protocol.delivery
  (:require [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as t]
            [cljs-time.coerce :refer [to-long
                                      from-long]]
            [status-im.utils.logging :as log]
            [status-im.protocol.state.delivery :as state]
            [status-im.protocol.user-handler :refer [invoke-user-handler]]
            [status-im.protocol.defaults :refer [max-retry-send-count
                                                 ack-wait-timeout
                                                 sending-retry-timeout
                                                 check-delivery-interval]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn needs-to-be-resent? [[_ {:keys [timestamp delivery-status]}]]
  (let [delivery-status (keyword delivery-status)]
    (or (and (= delivery-status :sent)
             (t/before? (t/plus (from-long timestamp) ack-wait-timeout) (t/now)))
        (and (= delivery-status :sending)
             (t/before? (t/plus (from-long timestamp) sending-retry-timeout) (t/now))))))

(defn delivery-pending-messages []
  (filter #(needs-to-be-resent? %) (state/pending-messages)))

(defn start-delivery-loop [send-message-fn]
  (go (loop [_ (<! (timeout check-delivery-interval))]
        (doseq [[message-id {:keys [chat-id retry-count]
                             :as message
                             :or {retry-count 0}}] (delivery-pending-messages)]
          (log/info "Delivery-loop: Message" message-id "is pending, retry-count=" retry-count)
          (if (< retry-count max-retry-send-count)
            (do
              (log/info "Delivery-loop: Re-sending message" message-id)
              (let [upd-message (assoc message :timestamp (to-long (t/now)))]
                (send-message-fn upd-message (fn [error _]
                                               (when-not error
                                                 (let [upd-message (merge upd-message
                                                                          {:retry-count     (inc retry-count)
                                                                           :delivery-status :sent})]
                                                   (state/add-pending-message message-id upd-message)
                                                   (invoke-user-handler :message-update upd-message)))))))
            (do
              (log/info "Delivery-loop: Retry-count for message" message-id "reached maximum")
              (let [internal? (state/internal? message-id)]
                (state/remove-pending-message message-id)
                (when-not internal?
                  (invoke-user-handler :message-failed {:message-id message-id
                                                        :chat-id    chat-id}))))))
        (recur (<! (timeout check-delivery-interval))))))