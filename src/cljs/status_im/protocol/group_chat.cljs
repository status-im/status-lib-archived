(ns status-im.protocol.group-chat
  (:require [status-im.protocol.state.state :as state :refer [connection
                                                              storage]]
            [status-im.protocol.state.delivery :refer [upsert-pending-message]]
            [status-im.protocol.state.group-chat :refer [get-keypair
                                                         get-peer-identities]]
            [status-im.protocol.web3 :refer [make-message]]))

(defn make-group-message [message-id group-id public-key payload type]
  (make-message {:from       (state/my-identity)
                 :message-id message-id
                 :chat-id    group-id
                 :send-once  false
                 :topics     [group-id]
                 :encrypt?   true
                 :public-key public-key
                 :payload    payload
                 :clear-info {:group-topic group-id
                              :type        type}}))

(defn send-group-message [{:keys [message-id group-id payload type internal?] :or {internal? false}}]
  (let [store       (storage)
        {public-key :public} (get-keypair store group-id)
        new-message (make-group-message message-id group-id public-key payload type)]
    (upsert-pending-message new-message {:identities (get-peer-identities store group-id)
                                         :internal?  internal?})
    new-message))

(defn init-group-chat-message [to group-topic identities keypair group-name]
  (make-message {:from      (state/my-identity)
                 :to        to
                 :send-once false
                 :payload   {:type        :group-init-chat
                             :group-topic group-topic
                             :group-name  group-name
                             :identities  identities
                             :keypair     keypair}}))

(defn group-add-participant-message [to group-id group-name identities keypair]
  (make-message {:from      (state/my-identity)
                 :to        to
                 :send-once false
                 :payload   {:type        :group-init-chat
                             :group-topic group-id
                             :group-name  group-name
                             :identities  identities
                             :keypair     keypair}}))

(defn group-remove-participant-message [to group-id keypair identity-to-remove]
  (make-message {:from      (state/my-identity)
                 :to        to
                 :send-once false
                 :payload   {:type             :group-removed-participant
                             :group-topic      group-id
                             :keypair          keypair
                             :removed-identity identity-to-remove}}))

(defn removed-from-group-message [group-id identity-to-remove]
  (make-message {:from      (state/my-identity)
                 :to        identity-to-remove
                 :send-once false
                 :payload   {:type        :group-you-have-been-removed
                             :group-topic group-id}}))