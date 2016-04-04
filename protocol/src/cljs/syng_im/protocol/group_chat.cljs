(ns syng-im.protocol.group-chat
  (:require [syng-im.protocol.state.state :as state :refer [connection
                                                            storage]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]
            [syng-im.protocol.state.group-chat :refer [get-keypair
                                                       get-peer-identities]]
            [syng-im.protocol.web3 :refer [make-msg
                                           post-msg]]))

(defn group-msg [group-id public-key payload type]
  (make-msg {:from       (state/my-identity)
             :topics     [group-id]
             :encrypt?   true
             :public-key public-key
             :payload    payload
             :clear-info {:group-topic group-id
                          :type        type}}))

(defn send-group-msg [{:keys [group-id payload type internal?] :or {internal? false}}]
  (let [store (storage)
        {public-key :public} (get-keypair store group-id)
        {:keys [msg-id msg] :as new-msg} (group-msg group-id public-key payload type)]
    (add-pending-message msg-id msg {:identities (get-peer-identities store group-id)
                                     :internal?  internal?})
    (post-msg (connection) msg)
    new-msg))

(defn init-group-chat-msg [to group-topic identities keypair group-name]
  (make-msg {:from    (state/my-identity)
             :to      to
             :payload {:type        :init-group-chat
                       :group-topic group-topic
                       :group-name  group-name
                       :identities  identities
                       :keypair     keypair}}))

(defn group-add-participant-msg [to group-id identities keypair]
  (make-msg {:from    (state/my-identity)
             :to      to
             :payload {:type        :init-group-chat
                       :group-topic group-id
                       :identities  identities
                       :keypair     keypair}}))

(defn group-remove-participant-msg [to group-id keypair identity-to-remove]
  (make-msg {:from    (state/my-identity)
             :to      to
             :payload {:type             :group-removed-participant
                       :group-topic      group-id
                       :keypair          keypair
                       :removed-identity identity-to-remove}}))

(defn removed-from-group-msg [group-id identity-to-remove]
  (make-msg {:from    (state/my-identity)
             :to      identity-to-remove
             :payload {:type        :removed-from-group
                       :group-topic group-id}}))