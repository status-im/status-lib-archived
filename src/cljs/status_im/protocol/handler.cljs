(ns status-im.protocol.handler
  (:require [cljs.reader :refer [read-string]]
            [status-im.utils.logging :as log]
            [status-im.utils.encryption :refer [decrypt]]
            [status-im.protocol.state.state :as state :refer [storage]]
            [status-im.protocol.state.delivery :refer [internal?
                                                       upsert-pending-message
                                                       get-pending-message
                                                       update-pending-message-identities]]
            [status-im.protocol.state.group-chat :refer [save-keypair
                                                         save-identities
                                                         get-identities
                                                         chat-exists?
                                                         get-keypair
                                                         add-identity
                                                         remove-group-data
                                                         save-group-admin
                                                         group-admin?
                                                         remove-identity
                                                         group-member?]]
            [status-im.protocol.discovery :refer [handle-discover-response]]
            [status-im.protocol.web3 :refer [to-utf8
                                             make-message
                                             listen
                                             stop-listener]]
            [status-im.protocol.user-handler :refer [invoke-user-handler]]
            [status-im.protocol.defaults :refer [default-content-type]]))

(declare handle-incoming-whisper-message)

(defn handle-ack [from {:keys [ack-message-id message-id] :as payload}]
  (log/info "Got ack for message:" ack-message-id "from:" from)
  (when-not (get-pending-message ack-message-id)
    (log/info "Got ack for message" ack-message-id "which isn't pending."))
  (let [internal-message? (internal? ack-message-id)]
    (update-pending-message-identities ack-message-id from)
    (when-not internal-message?
      (invoke-user-handler :message-delivered {:message-id ack-message-id
                                               :from       from}))
    (when-let [group-topic (payload :group-invite)]
      (invoke-user-handler :group-chat-invite-acked {:from           from
                                                     :ack-message-id ack-message-id
                                                     :group-id       group-topic}))))

(defn handle-seen [from {:keys [message-id]}]
  (log/info "Got seen for message:" message-id "from:" from)
  (invoke-user-handler :message-seen {:message-id message-id
                                      :from       from}))

(defn send-ack
  ([web3 from to message-id]
   (send-ack web3 from to message-id nil))
  ([web3 from to message-id ack-info]
   (when (and (not= to (state/my-identity))
              (not= from "0x0"))
     (log/info "Acking message:" message-id "To:" to)
     (let [new-message (make-message {:from      (state/my-identity)
                                      :send-once false
                                      :to        to
                                      :payload   (merge {:type           :ack
                                                         :ack-message-id message-id}
                                                        ack-info)})]
       (upsert-pending-message new-message)))))

(defn send-seen
  [web3 to message-id]
  (log/info "Send seen message:" message-id "To:" to)
  (let [new-message (make-message {:from      (state/my-identity)
                                   :to        to
                                   :send-once false
                                   :keep-id   false
                                   :payload   {:type       :seen
                                               :message-id message-id}})]
    (upsert-pending-message new-message)))

(defn handle-user-message [web3 to from {:keys [message-id] :as payload}]
  (send-ack web3 to from message-id)
  (invoke-user-handler :message-received {:from    to
                                          :to      from
                                          :payload payload}))

(defn handle-group-init-chat [web3 to from {:keys [group-topic keypair identities message-id group-name]}]
  (send-ack web3 to from message-id {:group-invite group-topic})
  (let [store (storage)]
    (when-not (chat-exists? store group-topic)
      (listen web3 handle-incoming-whisper-message {:topic [group-topic]})
      (save-keypair store group-topic keypair)
      (save-identities store group-topic identities)
      (save-group-admin store group-topic from)
      (invoke-user-handler :new-group-chat {:from       from
                                            :identities identities
                                            :group-id   group-topic
                                            :group-name group-name}))))

(defn decrypt-group-message [group-topic encrypted-payload]
  (let [store (storage)]
    (when-let [{private-key :private} (get-keypair store group-topic)]
      (try
        (-> (decrypt private-key encrypted-payload)
            (read-string)
            (assoc :group-topic group-topic))
        (catch :default e
          (log/warn "Failed to decrypt group message for group" group-topic e))))))

(defn handle-group-user-message [web3 to from {:keys [message-id group-topic] :as payload}]
  (send-ack web3 to from message-id)
  (invoke-user-handler :new-group-message {:from     from
                                           :group-id group-topic
                                           :payload  payload}))

(defn handle-group-new-participant [web3 to from {:keys [message-id identity group-topic]}]
  (let [store (storage)]
    (if (group-admin? store group-topic from)
      (do
        (send-ack web3 to from message-id)
        (when-not (group-member? store group-topic identity)
          (add-identity store group-topic identity)
          (invoke-user-handler :group-new-participant {:identity   identity
                                                       :group-id   group-topic
                                                       :from       from
                                                       :message-id message-id})))
      (log/warn "Ignoring group-new-participant for group" group-topic "from a non group-admin user" from))))

(defn handle-group-removed-participant [web3 to from {:keys [keypair group-topic message-id removed-identity]}]
  (let [store (storage)]
    (if (group-admin? store group-topic from)
      (do
        (send-ack web3 to from message-id)
        (when (group-member? store group-topic removed-identity)
          (save-keypair store group-topic keypair)
          (remove-identity store group-topic removed-identity)
          (invoke-user-handler :group-removed-participant {:identity   removed-identity
                                                           :group-id   group-topic
                                                           :from       from
                                                           :message-id message-id})))
      (log/warn "Ignoring group-removed-participant for group" group-topic "from a non group-admin user" from))))

(defn handle-group-you-have-been-removed [web3 to from {:keys [group-topic message-id]}]
  (let [store (storage)]
    (if (group-admin? store group-topic from)
      (do
        (send-ack web3 to from message-id)
        (when (group-member? store group-topic (state/my-identity))
          (remove-group-data store group-topic)
          (stop-listener [group-topic])
          (invoke-user-handler :removed-from-group {:group-id   group-topic
                                                    :from       from
                                                    :message-id message-id})))
      (log/warn "Ignoring removed-from-group for group" group-topic "from a non group-admin user" from))))

(defn handle-group-user-left [web3 to from {:keys [group-topic message-id]}]
  (let [store (storage)]
    (send-ack web3 to from message-id)
    (when (group-member? store group-topic from)
      (remove-identity store group-topic from)
      (invoke-user-handler :participant-left-group {:group-id   group-topic
                                                    :from       from
                                                    :message-id message-id}))))

(defn handle-group-message [web3 message-type to from {:keys [enc-payload group-topic]}]
  (if-let [payload (decrypt-group-message group-topic enc-payload)]
    (case message-type
      :group-user-message (handle-group-user-message web3 to from payload)
      :group-new-participant (handle-group-new-participant web3 to from payload)
      :group-participant-left (handle-group-user-left web3 to from payload))
    (log/debug "Could not decrypt group message, possibly you've left the group.")))

(defn handle-contact-update [from payload]
  (log/debug "Received contact-update message: " payload)
  (invoke-user-handler :contact-update {:from    from
                                        :payload payload}))

(defn handle-contact-online [from payload]
  (invoke-user-handler :contact-online {:from    from
                                        :payload payload}))

(defn handle-incoming-whisper-message [web3 message]
  (log/info "Got whisper message:" message)
  (let [{from    :from
         to      :to
         topics  :topics                                    ;; always empty (bug in go-ethereum?)
         payload :payload} (js->clj message :keywordize-keys true)]
    (if (or (= to "0x0")
            (= to (state/my-identity)))
      (let [{message-type :type :as payload} (->> (to-utf8 payload)
                                                  (read-string))]
        (case (keyword message-type)
          :ack (handle-ack from payload)
          :seen (handle-seen from payload)
          :user-message (handle-user-message web3 to from payload)
          :group-init-chat (handle-group-init-chat web3 to from payload)
          :group-you-have-been-removed (handle-group-you-have-been-removed web3 to from payload)
          :group-user-message (handle-group-message web3 message-type to from payload)
          :group-new-participant (handle-group-message web3 message-type to from payload)
          :group-participant-left (handle-group-message web3 message-type to from payload)
          :group-removed-participant (handle-group-removed-participant web3 to from payload)
          :discover-response (handle-discover-response web3 from payload)
          :contact-update (handle-contact-update from payload)
          :contact-online (handle-contact-online from payload)
          (if message-type
            (log/debug "Undefined message type: " (name message-type))
            (log/debug "Nil message type"))))
      (log/warn "My identity:" (state/my-identity) "Message To:" to "Message is encrypted for someone else, ignoring"))))
