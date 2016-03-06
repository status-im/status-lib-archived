(ns cljs-tests.protocol.delivery
  (:require [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as t]
            [cljs-tests.utils.logging :as log]
            [cljs-tests.protocol.state.delivery :as state]
            [cljs-tests.protocol.state.state :as s]
            [cljs-tests.protocol.whisper :as whisper]
            [cljs-tests.protocol.handler :as handler])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def max-retry-send-count 5)
(def ack-wait-timeout-ms (t/millis 5000))
(def check-delivery-interval-msg 100)

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
                (state/remove-pending-message msg-id)
                (handler/invoke-handler :delivery-failed {:msg-id msg-id})))))
        (recur (<! (timeout check-delivery-interval-msg))))))


(comment


  (take 30 (delivery-expired-seq))
  (state/add-pending-message 4 {:msg-id 4})

  @state/state

  )