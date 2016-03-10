(ns syng-im.protocol.group-chat
  (:require [syng-im.protocol.state.state :as state :refer [connection
                                                            storage]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]
            [syng-im.protocol.state.group-chat :refer [get-keypair
                                                       get-peer-identities]]
            [syng-im.protocol.web3 :refer [make-msg
                                           post-msg]]))

(defn send-group-msg [{:keys [group-id payload type internal?] :or {internal? false}}]
  (let [store (storage)
        {public-key :public} (get-keypair store group-id)
        {:keys [msg-id msg] :as new-msg} (make-msg {:from       (state/my-identity)
                                                    :topics     [group-id]
                                                    :encrypt?   true
                                                    :public-key public-key
                                                    :payload    payload
                                                    :clear-info {:group-id group-id
                                                                 :type     type}})]
    (add-pending-message msg-id msg {:identities (get-peer-identities store group-id)
                                     :internal?  internal?})
    (post-msg (connection) msg)
    new-msg))