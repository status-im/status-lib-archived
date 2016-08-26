(ns status-im.protocol.web3
  (:require [cljs.core.async :refer [chan put! close! <!]]
            [status-im.protocol.defaults :refer [status-message-ttl]]
            [status-im.utils.logging :as log]
            [status-im.utils.random :as random]
            [status-im.utils.encryption :refer [encrypt]]
            [status-im.protocol.state.state :as state]
            [status-im.protocol.user-handler :refer [invoke-user-handler]]
            [cljs-time.core :refer [now]]
            [cljs-time.coerce :refer [to-long]]
            cljsjs.web3)
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def web3 js/Web3)
(def status-app-topic "status-app")

(defn from-utf8 [s]
  (.fromUtf8 web3.prototype s))

(defn to-utf8 [s]
  (.toUtf8 web3.prototype s))

(defn whisper [web3]
  (.-shh web3))

(defn make-topics [topics]
  (->> {:topics (mapv from-utf8 topics)}
       (clj->js)))

(defn make-web3 [rpc-url]
  (->> (web3.providers.HttpProvider. rpc-url)
       (web3.)))

(defn make-callback [{:keys [error-message result-channel]}]
  (fn [error result]
    (if error
      (do
        (log/error (str error-message ":") error)
        (invoke-user-handler :error {:error-message error-message
                                     :details       error}))
      (put! result-channel result))
    (close! result-channel)))

(defn new-identity [web3]
  (let [result-channel (chan)
        callback       (make-callback {:error-message  "Call to newIdentity failed"
                                       :result-channel result-channel})]
    (.newIdentity (.-shh web3) callback)
    result-channel))

(defn create-identity [web3]
  (let [result-channel (chan)]
    (.sendAsync (.-currentProvider web3)
                (clj->js [{:jsonrpc "2.0" :method "shh_createIdentity" :params [] :id 99999999999}])
                (fn [error result]
                  (if error
                    (do
                      (log/error (str "Call to shh_createIdentity failed" ":") error)
                      (invoke-user-handler :error {:error-message "Call to shh_createIdentity failed"
                                                   :details       error}))
                    (let [[public private] (-> (js->clj result :keywordize-keys true)
                                               (first)
                                               :result)]
                      (put! result-channel {:public  public
                                            :private private})))
                  (close! result-channel)))
    result-channel))

(defn add-identity [web3 private-key]
  (let [result-channel (chan)]
    (.sendAsync (.-currentProvider web3)
                (clj->js [{:jsonrpc "2.0" :method "shh_addIdentity" :params [private-key] :id 99999999999}])
                (fn [error result]
                  (if error
                    (do
                      (log/error (str "Call to shh_addIdentity failed" ":") error)
                      (invoke-user-handler :error {:error-message "Call to shh_addIdentity failed"
                                                   :details       error}))
                    (put! result-channel (js->clj result)))
                  (close! result-channel)))
    result-channel))

(defn post-message
  ([web3 message]
   (post-message web3 message nil))
  ([web3 message callback]
   (let [js-message (clj->js message)]
     (log/info "Sending whisper message:" js-message)
     (-> (whisper web3)
         (.post js-message
                (or callback (fn [error result]
                               (when error
                                 (let [error-message "Call to shh.post() failed"]
                                   (log/error (str error-message ":") error)
                                   (invoke-user-handler :error {:error-message error-message
                                                                :details       error}))))))))))

(defn encrypt-payload [public-key clear-info payload-str]
  (->> (merge {:enc-payload (encrypt public-key payload-str)}
              clear-info)
       (str)))

(defn make-message
  "Returns pending message, `message` key is formed for Web3.shh.post()"
  [{:keys [from to chat-id ttl topics payload encrypt? public-key clear-info message-id keep-id send-once]
    :or   {ttl    status-message-ttl
           topics []}}]
  (let [message-id' (or message-id (random/id))]
    {:message-id  message-id'
     :chat-id     (or chat-id to)
     :message     (cond-> {:ttl     ttl
                           :topics  (->> (conj topics status-app-topic)
                                         (mapv from-utf8))
                           :payload (cond->> payload
                                             (not keep-id) (merge {:message-id message-id'})
                                             true (str)
                                             encrypt? (encrypt-payload public-key clear-info)
                                             true (from-utf8))}
                          from (assoc :from from)
                          to (assoc :to to))
     :send-once   send-once
     :retry-count 0
     :timestamp   0
     :status      :sending}))

(defn listen
  "Returns a filter which can be stopped with (stop-whisper-listener)"
  ([web3 handler]
   (listen web3 handler {}))
  ([web3 handler {:keys [topic] :or {topic []}}]
   (let [topic  (conj topic status-app-topic)
         shh    (whisper web3)
         filter (.filter shh (make-topics topic) (fn [error message]
                                                   (if error
                                                     (invoke-user-handler :error {:error-message error})
                                                     (handler web3 message))))]
     (log/debug "Listening to: " topic)
     (state/add-filter topic filter))))

(defn stop-listener [topic]
  (let [topic  (conj topic status-app-topic)
        filter (state/get-filter topic)]
    (when filter
      (.stopWatching filter)
      (state/remove-filter topic))))

(defn stop-watching-filters []
  (doseq [filter (state/get-all-filters)]
    (.stopWatching filter))
  (state/remove-all-filters))
