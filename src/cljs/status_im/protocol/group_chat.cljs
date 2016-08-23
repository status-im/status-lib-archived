(ns status-im.protocol.group-chat
  (:require [status-im.protocol.state.state :as state :refer [connection
                                                              storage]]
            [status-im.protocol.state.delivery :refer [add-pending-message]]
            [status-im.protocol.state.group-chat :refer [get-keypair
                                                         get-peer-identities]]
            [status-im.protocol.web3 :refer [make-message
                                             post-message]]))

(defn make-group-message [group-id public-key payload type]
  (make-message {:from       (state/my-identity)
                 :topics     [group-id]
                 :encrypt?   true
                 :public-key public-key
                 :payload    payload
                 :clear-info {:group-topic group-id
                              :type        type}}))

(defn send-group-message
  ([message]
   (send-group-message message nil nil))
  ([{:keys [group-id payload type internal?] :or {internal? false}} src-message callback]
   (let [store (storage)
         {public-key :public} (get-keypair store group-id)
         {:keys [message-id message] :as new-message} (make-group-message group-id public-key payload type)]
     (when src-message
       (add-pending-message message-id src-message {:identities (get-peer-identities store group-id)
                                                    :internal?  internal?}))
     (post-message (connection) message callback)
     new-message)))

(defn init-group-chat-message [to group-topic identities keypair group-name]
  (make-message {:from    (state/my-identity)
                 :to      to
                 :payload {:type        :group-init-chat
                           :group-topic group-topic
                           :group-name  group-name
                           :identities  identities
                           :keypair     keypair}}))

(defn group-add-participant-message [to group-id group-name identities keypair]
  (make-message {:from    (state/my-identity)
                 :to      to
                 :payload {:type        :group-init-chat
                           :group-topic group-id
                           :group-name  group-name
                           :identities  identities
                           :keypair     keypair}}))

(defn group-remove-participant-message [to group-id keypair identity-to-remove]
  (make-message {:from    (state/my-identity)
                 :to      to
                 :payload {:type             :group-removed-participant
                           :group-topic      group-id
                           :keypair          keypair
                           :removed-identity identity-to-remove}}))

(defn removed-from-group-message [group-id identity-to-remove]
  (make-message {:from    (state/my-identity)
                 :to      identity-to-remove
                 :payload {:type        :group-you-have-been-removed
                           :group-topic group-id}}))