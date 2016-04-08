(ns syng-im.protocol.discovery
  (:require [syng-im.protocol.state.state :as state :refer [connection
                                                            storage]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]
            [syng-im.protocol.state.group-chat :refer [get-keypair
                                                       get-peer-identities]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.protocol.web3 :refer [make-msg
                                           post-msg]]))

(defn get-next-latitude [latitude]
  (let [base-latitude (int latitude)]
    if (< base-latitude 90) (inc base-latitude) (- base-latitude 1)))

(defn get-next-longitude [longitude]
  (let [base-longitude (int longitude)]
    if (< base-longitude 180) (inc base-longitude) -180))

(defn discover-in-proximity [{:keys [latitude longitude]} hashtags]
  (let [base-latitude (int latitude)
        base-longitude (int longitude)
        next-latitude (get-next-latitude latitude)
        next-longitude (get-next-longitude longitude)
        topics [(clojure.string/join "," [base-latitude base-longitude])
                (clojure.string/join "," [next-latitude base-longitude])
                (clojure.string/join "," [base-latitude next-longitude])
                (clojure.string/join "," [next-latitude next-longitude])]
        payload {:type :discover
                 :hashtags hashtags}]
    make-msg {:from (state/my-identity)
              :topics topics
              :payload payload}))

(defn discover [hashtags]
  (let [topics (map #(str "syng-search-" %) hashtags)
        payload {:type discover
                 :hashtags hashtags}]
    make-msg {:from (state/my-identity)
              :topics topics
              :payload payload}))

(defn handle-discover-message [web3 from payload]
  (let [hashtags (:hashtags payload)
        to (:from payload)]
    (send-discovery-response to hashtags)))

(defn handle-discover-response[web3 from payload]
  (let [location (:location payload)
        hashtags (:hashtags payload)]
    ;; send response data to app. how ???
    (invoke-user-handler :discover-response {:from    from
                                 :payload payload})))


(defn get-location []
  {:latitude 10.5 :longitude 4.7})

(defn send-discovery-response [to, hastags]
  (let [payload {:type :discover-response
                 :location (get-location)
                 :status (state/status)
                 :name (state/name)
                 :hashtags hashtags}]
  make-msg {:from (state/my-identity)
            :to to
            :payload payload}))

(comment

  (get-next-latitude 40.5)

  (get-next-latitude 90)

  (get-next-longitude 40.5)

  (get-next-longitude 180)

  (discover-in-proximity {:latitude 90 :longitude 180} [])

  (discover ["test" "lala"])
  )
