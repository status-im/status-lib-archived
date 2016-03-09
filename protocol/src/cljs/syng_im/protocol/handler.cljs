(ns syng-im.protocol.handler
  (:require [cljs.reader :refer [read-string]]
            [syng-im.utils.logging :as log]
            [syng-im.utils.encryption :refer [decrypt]]
            [syng-im.protocol.state.state :as state :refer [storage]]
            [syng-im.protocol.state.delivery :refer [internal?
                                                     update-pending-message]]
            [syng-im.protocol.state.group-chat :refer [save-keypair
                                                       save-identities
                                                       chat-exists?
                                                       get-keypair]]
            [syng-im.protocol.web3 :refer [to-ascii
                                           make-msg
                                           post-msg
                                           listen]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]))

(defn handle-ack [from {:keys [ack-msg-id] :as payload}]
  (log/info "Got ack for message:" ack-msg-id "from:" from)
  (let [internal-message? (internal? ack-msg-id)]
    (update-pending-message ack-msg-id from)
    (when-not internal-message?
      (invoke-user-handler :msg-acked {:msg-id ack-msg-id
                                       :from   from}))
    (when-let [group-topic (payload :group-invite)]
      (invoke-user-handler :group-chat-invite-acked {:from     from
                                                     :group-id group-topic}))))

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

(defn handle-user-msg [web3 from {:keys [msg-id] :as payload}]
  (send-ack web3 from msg-id)
  (invoke-user-handler :new-msg {:from    from
                                 :payload payload}))

(declare handle-incoming-whisper-msg)

(defn handle-new-group-chat [web3 from {:keys [group-topic keypair identities msg-id]}]
  (send-ack web3 from msg-id {:group-invite group-topic})
  (let [store (storage)]
    (when-not (chat-exists? store group-topic)
      (listen web3 handle-incoming-whisper-msg {:topics [group-topic]})
      (save-keypair store group-topic keypair)
      (save-identities store group-topic identities)
      (invoke-user-handler :new-group-chat {:from       from
                                            :identities identities
                                            :group-id   group-topic}))))

(defn handle-group-user-msg [web3 from {:keys [enc-payload group-id]}]
  (let [store (storage)
        {private-key :private} (get-keypair store group-id)
        {:keys [msg-id] :as payload} (-> (decrypt private-key enc-payload)
                                         (read-string)
                                         (assoc :group-id group-id))]
    (send-ack web3 from msg-id)
    (invoke-user-handler :new-group-msg {:from    from
                                         :payload payload})))

(defn handle-incoming-whisper-msg [web3 msg]
  (log/info "Got whisper message:" msg)
  (let [{from    :from
         to      :to
         topics  :topics                                    ;; always empty (bug in go-ethereum?)
         payload :payload
         :as     msg} (js->clj msg :keywordize-keys true)]
    (if (or (= to "0x0")
            (= to (state/my-identity)))
      (let [{msg-type :type :as payload} (->> (to-ascii payload)
                                              (read-string))]
        (case msg-type
          :ack (handle-ack from payload)
          :user-msg (handle-user-msg web3 from payload)
          :init-group-chat (handle-new-group-chat web3 from payload)
          :group-user-msg (handle-group-user-msg web3 from payload)))
      (log/warn "My identity:" (state/my-identity) "Message To:" to "Message is encrypted for someone else, ignoring"))))
