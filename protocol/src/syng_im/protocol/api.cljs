(ns syng-im.protocol.api
  (:require [cljs.core.async :refer [<! timeout]]
            [syng-im.protocol.state.state :as state]
            [syng-im.protocol.delivery :as delivery]
            [syng-im.protocol.state.delivery :as delivery-state]
            [syng-im.protocol.whisper :as whisper]
            [syng-im.protocol.handler :as h])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def default-content-type "text/plain")

(defn create-connection [ethereum-rpc-url]
  (whisper/make-web3 ethereum-rpc-url))

(defn create-identity [connection]
  (whisper/new-identity connection))

(defn init-protocol
  "Required [handler ethereum-rpc-url]
   Optional [whisper-identity] - if not passed a new identity is created automatically

   (fn handler [{:keys [event-type...}])

   :event-type can be:

   :new-msg - [from payload]
   :error - [error-msg details]
   :msg-acked [msg-id]
   :delivery-failed [msg-id]
   :initialized [identity]

   :new-msg, msg-acked should be handled idempotently (may be called multiple times for the same msg-id)
   "
  [{:keys [handler ethereum-rpc-url identity]}]
  (state/set-handler handler)
  (go
    (let [connection (create-connection ethereum-rpc-url)
          identity   (or identity
                         (<! (create-identity connection)))]
      (state/set-connection connection)
      (state/set-identity identity)
      (whisper/listen connection)
      (delivery/start-delivery-loop)
      (h/invoke-handler :initialized {:identity identity}))))

(defn send-user-msg [{:keys [to content]}]
  (let [{:keys [msg-id msg] :as new-msg} (whisper/make-msg {:from    (state/my-identity)
                                                            :to      to
                                                            :payload {:content      content
                                                                      :content-type default-content-type
                                                                      :type         :user-msg}})]
    (delivery-state/add-pending-message msg-id msg)
    (whisper/post-msg (state/connection) msg)
    new-msg))

(defn my-identity []
  (state/my-identity))

(defn current-connection []
  (state/connection))