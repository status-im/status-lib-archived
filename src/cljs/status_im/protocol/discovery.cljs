(ns status-im.protocol.discovery
  (:require [status-im.protocol.state.state :as state :refer [connection
                                                              storage]]
            [status-im.protocol.state.discovery :refer [save-status
                                                        get-name
                                                        get-photo-path
                                                        get-status
                                                        get-hashtags]]
            [status-im.protocol.state.delivery :refer [upsert-pending-message]]
            [status-im.protocol.user-handler :refer [invoke-user-handler]]
            [status-im.utils.logging :as log]
            [status-im.protocol.web3 :refer [make-message]]
            [cljs-time.core :refer [now]]
            [cljs-time.coerce :refer [to-long]]
            [status-im.utils.random :as random]))

(def discovery-topic "status-discovery")
(def discovery-user-topic "status-user-")
(def discovery-hashtag-topic "status-hashtag-")

(def daily-broadcast-ttl (* 60 60 24))
(def weekly-broadcast-ttl (* daily-broadcast-ttl 7))
(def monthly-broadcast-ttl (* daily-broadcast-ttl 30))

(defn set-interval
  "Invoke the given function after and every delay milliseconds."
  [delay f]
  (js/setInterval f delay))

(defn hashtags->topics
  "Create listen topic from hashtags."
  [hashtags]
  (->> (distinct hashtags)
       (sort)
       (mapv #(str discovery-hashtag-topic %))
       (mapv #(vector % discovery-topic))))

(defn user-topic
  "Create listen topic for user identity"
  [identity]
  (str discovery-user-topic identity))

(defn create-discover-message
  "Create discovery message"
  [topic payload ttl to message-id]
  (let [data {:message-id message-id
              :send-once  true
              :from       (state/my-identity)
              :topics     topic
              :ttl        ttl
              :payload    payload}]
    (->> (cond-> data
                 to (assoc :to to))
         (make-message))))

(defn send-discover-messages
  "Send discover messages for each topic"
  [{:keys [topics payload ttl to] :as message :or {ttl weekly-broadcast-ttl
                                                   to  nil}}]
  (let [message-id (random/id)]
    (log/debug (str "Sending discover status messages with id " message-id " for each topic: ") message)
    (doseq [topic topics]
      (let [new-message (create-discover-message topic payload ttl to message-id)]
        (upsert-pending-message new-message)))))

(defn broadcast-status
  "Broadcast discover message if we have hashtags."
  [topics]
  (let [name       (get-name)
        photo-path (get-photo-path)
        status     (get-status)
        hashtags   (get-hashtags)]
    (send-discover-messages {:topics  topics
                             :payload {:name       name
                                       :photo-path photo-path
                                       :status     status
                                       :hashtags   hashtags
                                       :type       :discover-response}})))

(defn broadcast-account-update
  "Broadcast discover message if we have hashtags."
  [topics account]
  (send-discover-messages {:topics  topics
                           :ttl     monthly-broadcast-ttl
                           :payload {:account account
                                     :type    :contact-update}}))

(defn broadcast-online
  "Broadcast user's online presence"
  [topics]
  (send-discover-messages {:topics  topics
                           :ttl     daily-broadcast-ttl
                           :payload {:at   (to-long (now))
                                     :type :contact-online}}))

(defn do-periodically
  "Do something periodically"
  [interval func]
  (func)
  (set-interval interval func))

(defn handle-discover-response
  "Handle discover-response messages."
  [web3 from payload]
  (log/debug "Received discover-response message: " payload)
  (when (not (= (state/my-identity) from))
    (invoke-user-handler :discover-response {:from    from
                                             :payload payload})))
