(ns syng-im.protocol.discovery
  (:require [syng-im.protocol.state.state :as state :refer [connection
                                                            storage]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]
            [syng-im.protocol.state.discovery :refer [save-status
                                                      get-status]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.protocol.web3 :refer [make-msg
                                           post-msg]]))

(def discovery-response-topic "status-discovery-responses")
(def discovery-search-topic "status-discovery-searches")
(def discovery-hashtag-topic "status-search-")

(defn set-interval
  "Invoke the given function after and every delay milliseconds."
  [delay f]
  (js/setInterval f delay))

(defn get-hashtag-topics [hashtags]
  (map #(str discovery-hashtag-topic %) hashtags))

(defn get-location []
  ;; TODO: get location
  {:latitude 10.5 :longitude 4.7})

(defn discover-message
  ([payload type]
   (discover-message payload type nil))
  ([payload type to]
   (if to
     (make-msg {:from       (state/my-identity)
                :to         to
                :topics     [discovery-response-topic]
                :payload    payload
                :clear-info {:type type}})
     (make-msg {:from       (state/my-identity)
                :topics     [discovery-response-topic]
                :payload    payload
                :clear-info {:type type}}))))

(defn discover-message
  ([payload type]
   (discover-message payload type nil))
  ([payload type to]
   (->> (cond-> {:from       (state/my-identity)
                 :topics     [discovery-response-topic]
                 :payload    payload
                 :clear-info {:type type}}
                to (assoc :to to))
        (make-msg))))

(defn send-discover-message [{:keys [payload type to] :or {to nil}}]
  (let [store (storage)
        {:keys [msg-id msg] :as new-msg} (discover-message payload type to)]
    (add-pending-message msg-id msg)
    (post-msg (connection) msg)
    new-msg))

(defn send-broadcast-status [status]
  (send-discover-message {:type    :discover-broadcast
                          :payload {:status   status
                                    :location (get-location)}}))

(defn broadcast-status []
  (let [status (get-status)]
    (if (not (clojure.string/blank? status))
      (send-broadcast-status status))))

(defn broadcast-status []
  (let [status (get-status)]
    (when-not (clojure.string/blank? status)
      (send-broadcast-status status))))

(defn init-discovery []
  (set-interval 1800 broadcast-status))

(defn update-status [status]
  (save-status status))

(defn discovery-search-message [hashtags]
  (let [topics  (get-hashtag-topics hashtags)
        payload {:type     :discovery-search
                 :hashtags hashtags}]
    make-msg {:from    (state/my-identity)
              :topics  topics
              :payload payload}))

;; ^ missing parens

(defn handle-discover-response [web3 from payload]
  (let [location (:location payload)
        hashtags (:hashtags payload)]
    ;; send response data to app. how ???
    (invoke-user-handler :discover-response {:from    from
                                             :payload payload})))

(defn handle-discover-response [web3 from {:keys [location hashtags] :as payload}]
  ;; send response data to app. how ???
  (invoke-user-handler :discover-response {:from    from
                                           :payload payload}))

(defn send-discovery-response [to, hashtags]
  (let [status (get-status)]
    (send-discover-message {:type    :discovery-response
                            :to      to
                            :payload {:status   status
                                      :hashtags hashtags
                                      :location (get-location)}})
    ))

(defn handle-discovery-search [web3 from payload]
  (let [hashtags (:hashtags payload)
        to       (:from payload)]
    (send-discovery-response to hashtags)))






(defn get-next-latitude [latitude]
  (let [base-latitude (int latitude)]
    (if (< base-latitude 90) (inc base-latitude) (- base-latitude 1))))

(defn get-next-latitude [latitude]
  (let [base-latitude (int latitude)]
    (if (< base-latitude 90)
      (inc base-latitude)
      (- base-latitude 1))))

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
                        :hashtags hashtags}]
    make-msg {:from    (state/my-identity)
              :topics  topics
              :payload payload}))

;; missing parens ^

(comment

  (get-next-latitude 40.5)

  (get-next-latitude 90)

  (get-next-longitude 40.5)

  (get-next-longitude 180)

  (discover-in-proximity {:latitude 90 :longitude 180} [])

  (discover ["test" "lala"])
  )
