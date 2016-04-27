(ns syng-im.protocol.handler
  (:require [cljs.reader :refer [read-string]]
            [syng-im.utils.logging :as log]
            [syng-im.utils.encryption :refer [decrypt]]
            [syng-im.protocol.state.state :as state :refer [storage]]
            [syng-im.protocol.state.delivery :refer [internal?
                                                     pending?
                                                     update-pending-message]]
            [syng-im.protocol.state.group-chat :refer [save-keypair
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
            [syng-im.protocol.discovery :refer [handle-discovery-search
                                                handle-discover-response]]
            [syng-im.protocol.web3 :refer [to-ascii
                                           make-msg
                                           post-msg
                                           listen
                                           stop-listener]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.protocol.defaults :refer [default-content-type]]))

(defn handle-ack [from {:keys [ack-msg-id msg-id] :as payload}]
  (log/info "Got ack for message:" ack-msg-id "from:" from)
  (when-not (pending? ack-msg-id)
    (log/info "Got ack for message" ack-msg-id "which isn't pending."))
  (let [internal-message? (internal? ack-msg-id)]
    (update-pending-message ack-msg-id from)
    (when-not internal-message?
      (invoke-user-handler :msg-acked {:msg-id ack-msg-id
                                       :from   from}))
    (when-let [group-topic (payload :group-invite)]
      (invoke-user-handler :group-chat-invite-acked {:from       from
                                                     :ack-msg-id ack-msg-id
                                                     :group-id   group-topic}))))

(defn send-ack
  ([web3 to msg-id]
   (send-ack web3 to msg-id nil))
  ([web3 to msg-id ack-info]
   (log/info "Acking message:" msg-id "To:" to)
   (let [{:keys [msg]} (make-msg {:from    (state/my-identity)
                                  :to      to
                                  :payload (merge {:type       :ack
                                                   :ack-msg-id msg-id}
                                                  ack-info)})]
     (post-msg web3 msg))))

(defn handle-user-msg [web3 from to {:keys [msg-id] :as payload}]
  (send-ack web3 from msg-id)
  (invoke-user-handler :new-msg {:from    from
                                 :to      to
                                 :payload payload}))

(declare handle-incoming-whisper-msg)

(defn handle-new-group-chat [web3 from {:keys [group-topic keypair identities msg-id]}]
  (send-ack web3 from msg-id {:group-invite group-topic})
  (let [store (storage)]
    (when-not (chat-exists? store group-topic)
      (listen web3 handle-incoming-whisper-msg {:topics [group-topic]})
      (save-keypair store group-topic keypair)
      (save-identities store group-topic identities)
      (save-group-admin store group-topic from)
      (invoke-user-handler :new-group-chat {:from       from
                                            :identities identities
                                            :group-id   group-topic}))))

(defn decrypt-group-msg [group-topic encrypted-payload]
  (let [store (storage)]
    (when-let [{private-key :private} (get-keypair store group-topic)]
      (-> (decrypt private-key encrypted-payload)
          (read-string)
          (assoc :group-topic group-topic)))))

(defn handle-group-user-msg [web3 from {:keys [msg-id group-topic] :as payload}]
  (send-ack web3 from msg-id)
  (invoke-user-handler :new-group-msg {:from     from
                                       :group-id group-topic
                                       :payload  payload}))

(defn handle-group-new-participant [web3 from {:keys [msg-id identity group-topic]}]
  (let [store (storage)]
    (if (group-admin? store group-topic from)
      (do
        (send-ack web3 from msg-id)
        (when-not (group-member? store group-topic identity)
          (add-identity store group-topic identity)
          (invoke-user-handler :group-new-participant {:identity identity
                                                       :group-id group-topic
                                                       :from     from
                                                       :msg-id   msg-id})))
      (log/warn "Ignoring group-new-participant for group" group-topic "from a non group-admin user" from))))

(defn handle-group-removed-participant [web3 from {:keys [keypair group-topic msg-id removed-identity]}]
  (let [store (storage)]
    (if (group-admin? store group-topic from)
      (do
        (send-ack web3 from msg-id)
        (when (group-member? store group-topic removed-identity)
          (save-keypair store group-topic keypair)
          (remove-identity store group-topic removed-identity)
          (invoke-user-handler :group-removed-participant {:identity removed-identity
                                                           :group-id group-topic
                                                           :from     from
                                                           :msg-id   msg-id})))
      (log/warn "Ignoring group-removed-participant for group" group-topic "from a non group-admin user" from))))

(defn handle-removed-from-group [web3 from {:keys [group-topic msg-id]}]
  (let [store (storage)]
    (if (group-admin? store group-topic from)
      (do
        (send-ack web3 from msg-id)
        (when (group-member? store group-topic (state/my-identity))
          (remove-group-data store group-topic)
          (stop-listener group-topic)
          (invoke-user-handler :removed-from-group {:group-id group-topic
                                                    :from     from
                                                    :msg-id   msg-id})))
      (log/warn "Ignoring removed-from-group for group" group-topic "from a non group-admin user" from))))

(defn handle-participant-left-group [web3 from {:keys [group-topic msg-id]}]
  (let [store (storage)]
    (send-ack web3 from msg-id)
    (when (group-member? store group-topic from)
      (remove-identity store group-topic from)
      (invoke-user-handler :participant-left-group {:group-id group-topic
                                                    :from     from
                                                    :msg-id   msg-id}))))

(defn handle-group-msg [web3 msg-type from {:keys [enc-payload group-topic]}]
  (if-let [payload (decrypt-group-msg group-topic enc-payload)]
    (case msg-type
      :group-user-msg (handle-group-user-msg web3 from payload)
      :group-new-participant (handle-group-new-participant web3 from payload)
      :left-group (handle-participant-left-group web3 from payload))
    (log/debug "Could not decrypt group msg, possibly you've left the group.")))

(defn handle-incoming-whisper-msg [web3 msg]
  (log/info "Got whisper message:" msg)
  (let [{from    :from
         to      :to
         topics  :topics                                    ;; always empty (bug in go-ethereum?)
         payload :payload} (js->clj msg :keywordize-keys true)]
    (if (or (= to "0x0")
            (= to (state/my-identity)))
      (let [{msg-type :type :as payload} (->> (to-ascii payload)
                                              (read-string))]
        (case msg-type
          :ack (handle-ack from payload)
          :user-msg (handle-user-msg web3 from to payload)
          :init-group-chat (handle-new-group-chat web3 from payload)
          :group-removed-participant (handle-group-removed-participant web3 from payload)
          :removed-from-group (handle-removed-from-group web3 from payload)
          :group-user-msg (handle-group-msg web3 msg-type from payload)
          :group-new-participant (handle-group-msg web3 msg-type from payload)
          :left-group (handle-group-msg web3 msg-type from payload)
          :discovery-search (handle-discovery-search web3 from payload)
          :discover-response (handle-discover-response web3 from payload)
          (if msg-type
            (log/debug "Undefined message type: " (name msg-type))
            (log/debug "Nil message type"))))
      (log/warn "My identity:" (state/my-identity) "Message To:" to "Message is encrypted for someone else, ignoring"))))
