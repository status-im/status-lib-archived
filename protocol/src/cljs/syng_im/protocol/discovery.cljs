(ns syng-im.protocol.discovery
  (:require [syng-im.protocol.state.state :as state :refer [connection
                                                            storage]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]
            [syng-im.protocol.state.discovery :refer [save-status
                                                      get-name
                                                      get-status
                                                      get-hashtags]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.utils.logging :as  log]
            [syng-im.protocol.web3 :refer [make-msg
                                           post-msg]]))

(def discovery-response-topic "status-discovery-responses")
(def discovery-search-topic "status-discovery-searches")
(def discovery-hashtag-topic "status-search-")
(def broadcast-interval 1800000)

(defn set-interval
  "Invoke the given function after and every delay milliseconds."
  [delay f]
  (js/setInterval f delay))

(defn get-hashtag-topics
  "Create listen topic from hastags."
  [hashtags]
  (map #(str discovery-hashtag-topic %) hashtags))

(defn discover-response-message
  "Create discover response message."
  ([payload]
   (discover-response-message payload nil))
  ([payload to]
   (let [data {:from       (state/my-identity)
               :topics     [discovery-response-topic]
               :payload    payload}
         _ (log/debug "Creating discover message using: " data)]
   (->> (cond-> data
                to (assoc :to to))
        (make-msg)))))

(defn send-discover-message
  "Send discover message to network."
  [{:keys [payload to] :or {to nil}}]
  (log/debug "Sending discover status: " payload to)
  (let [{:keys [msg-id msg] :as new-msg} (discover-response-message payload to)]
    (post-msg (connection) msg)
    new-msg))

(defn send-broadcast-status
  "Broadcast discover message."
  [to hashtags location]
  (let [name (get-name)
        status (get-status)]
    (send-discover-message {:payload {:name name
                                      :status status
                                      :hashtags hashtags
                                      :type :discover-response
                                      :location location}
                            :to to})))

(defn broadcast-status
  "Broadcast discover message if we have hashtags."
  ([]
   (broadcast-status nil))
  ([to]
   (let [hashtags (get-hashtags)]
    (when (pos? (count hashtags))
      (.getCurrentPosition (.-geolocation js/navigator)
                           #(send-broadcast-status to hashtags %)
                           #(send-broadcast-status to hashtags nil)
                           {:enableHighAccuracy false
                            :timeout 20000
                            :maximumAge 1000})))))

(defn init-discovery
  "Initialize broadcasting discover message."
  []
  (set-interval broadcast-interval broadcast-status))

(defn discovery-search-message [hashtags]
  (let [topics  (get-hashtag-topics hashtags)
        payload {:type     :discovery-search
                 :hashtags hashtags}]
    make-msg {:from    (state/my-identity)
              :topics  topics
              :payload payload}))

(defn handle-discover-response
  "Handle discover-response messages."
  [web3 from payload]
  (log/debug "Received discover-response message: " payload)
  (when (not (= (state/my-identity) from))
    (invoke-user-handler :discover-response {:from    from
                                           :payload payload})))

(defn handle-discovery-search
  "Handle discover-search messages."
  [web3 from payload]
  (log/debug "Received discover-search message: " payload)
  (broadcast-status from))

(defn get-next-latitude [latitude]
  (let [base-latitude (int latitude)]
    (if (< base-latitude 90)
      (inc base-latitude)
      (dec base-latitude))))

(defn get-next-longitude [longitude]
  (let [base-longitude (int longitude)]
    (if (< base-longitude 180) (inc base-longitude) -180)))

(defn discover-in-proximity [{:keys [latitude longitude]} hashtags]
  (let [base-latitude  (int latitude)
        base-longitude (int longitude)
        next-latitude  (get-next-latitude latitude)
        next-longitude (get-next-longitude longitude)
        topics         [(clojure.string/join "," [base-latitude base-longitude])
                        (clojure.string/join "," [next-latitude base-longitude])
                        (clojure.string/join "," [base-latitude next-longitude])
                        (clojure.string/join "," [next-latitude next-longitude])]
        payload        {:type     :discover
                        :hashtags hashtags}]
    (make-msg {:from    (state/my-identity)
              :topics  topics
              :payload payload})))

(comment

  (get-next-latitude 40.5)

  (get-next-latitude 90)

  (get-next-longitude 40.5)

  (get-next-longitude 180)

  (discover-in-proximity {:latitude 90 :longitude 180} [])

  (.getCurrentPosition (.-geolocation js/navigator) #(.log js/console %) #(.log js/console %) {:enableHighAccuracy true, :timeout 20000, :maximumAge 1000})
  )
