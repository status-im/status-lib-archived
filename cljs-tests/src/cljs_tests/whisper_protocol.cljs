(ns cljs-tests.whisper-protocol
  (:require [cljsjs.web3]
            [cljsjs.chance]))


(def pending-messages (atom {}))
(def syng-app-topic "SYNG-APP-CHAT-TOPIC")
(def syng-msg-ttl 100)
(def default-content-type "text/plain")

(defn from-ascii [s]
  (.fromAscii js/Web3.prototype s))

(defn whisper [web3]
  (.-shh web3))

(defn make-web3 [rpc-url]
  (->> (js/Web3.providers.HttpProvider. rpc-url)
       (js/Web3.)))

(defn make-topics [topics]
  (->> {:topics (mapv from-ascii topics)}
       (clj->js)))

(defn new-identity [web3]
  (.newIdentity (.-shh web3)))

(defn whisper-listen
  "Returns a filter which can be used with (stop-whisper-listener)"
  [web3 on-error]
  (let [shh    (whisper web3)
        filter (.filter shh (make-topics [syng-app-topic]))
        watch  (.watch filter (fn [error result]
                                (if error
                                  (on-error error)
                                  (println "got whisper msg:" (js->clj result)))))]
    filter))

(defn stop-whisper-listener [filter]
  (.stopWatching filter))

(defn send-message
  ([web3 from to content]
   (let [msg-id      (.guid js/chance)
         msg         (cond-> {:ttl     syng-msg-ttl
                              :topics  [(from-ascii syng-app-topic)]
                              :payload (from-ascii content)
                              ;(->> (str {:msg-id       msg-id
                              ;           :msg-num      122
                              ;           :type         :user-message
                              ;           :content      encoded-msg
                              ;           :content-type default-content-type})
                              ;     (from-ascii))
                              :from    from}
                             to (assoc :to to))]
     (println "sending:" msg)
     (.log js/console (clj->js msg))
     (swap! pending-messages assoc msg-id msg)
     (.post (.-shh web3)
            (clj->js
              (dissoc msg :from :to)))
     ))
  ([web3 from content]
   (send-message web3 from nil content)))
