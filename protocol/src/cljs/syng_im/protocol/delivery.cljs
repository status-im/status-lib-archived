(ns syng-im.protocol.delivery
  (:require [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as t]
            [syng-im.utils.logging :as log]
            [syng-im.protocol.state.delivery :as state]
            [syng-im.protocol.state.state :as s]
            [syng-im.protocol.web3 :as whisper]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.protocol.defaults :refer [max-retry-send-count
                                               ack-wait-timeout-ms
                                               check-delivery-interval-msg]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn expired? [timestamp]
  (t/before? (t/plus timestamp ack-wait-timeout-ms) (t/now)))

(defn delivery-expired-seq []
  (lazy-seq
    (let [{:keys [timestamp] :as item} (->> (state/delivery-queue)
                                            (peek))]
      (when timestamp
        (if (expired? timestamp)
          (do (state/pop-delivery-queue)
              (cons item (delivery-expired-seq)))
          nil)))))

(defn start-delivery-loop []
  (go (loop [_ (<! (timeout check-delivery-interval-msg))]
        (doseq [{:keys [msg-id]} (delivery-expired-seq)]
          (log/info "Delivery-loop:" "Checking delivery of msg-id" msg-id)
          (when-let [{:keys [retry-count msg]} (state/pending? msg-id)]
            (log/info "Delivery-loop: Message" msg-id "is pending, retry-count=" retry-count)
            (if (< retry-count max-retry-send-count)
              (do
                (log/info "Delivery-loop: Re-sending message" msg-id)
                (whisper/post-msg (s/connection) msg)
                (state/push-delivery-queue msg-id)
                (state/inc-retry-count msg-id))
              (do
                (log/info "Delivery-loop: Retry-count for message" msg-id "reached maximum")
                (let [internal? (state/internal? msg-id)]
                  (state/remove-pending-message msg-id)
                  (when-not internal?
                    (invoke-user-handler :delivery-failed {:msg-id msg-id})))))))
        (recur (<! (timeout check-delivery-interval-msg))))))


(comment


  (take 30 (delivery-expired-seq))
  (state/add-pending-message 4 {:msg-id 4})

  @state/state

  )