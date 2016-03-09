(ns syng-im.core
  (:require [clojure.string :as s]
            [syng-im.protocol.api :as p]
            [syng-im.utils.logging :as log]
            [goog.dom :as g]
            [goog.dom.forms :as f]
            [goog.events :as e]
            [goog.events.EventType]
            [goog.events.KeyCodes]
            [goog.events.KeyHandler]
            [goog.events.KeyHandler.EventType :as key-handler-events]
            [syng-im.protocol.state.storage :as st])
  (:import [goog.events EventType]
           [goog.events KeyCodes]))

(enable-console-print!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defrecord MapStore [m]
  st/Storage
  (put [{:keys [m]} key value]
    (swap! m assoc key value))
  (get [{:keys [m]} key]
    (get @m key))
  (contains-key? [{:keys [m]} key]
    (contains? @m key)))

(defonce state (atom {:group-id nil
                      :storage  (map->MapStore {:m (atom {})})}))

(defn add-to-chat [element-id from content]
  (let [chat-area (g/getElement element-id)
        chat      (f/getValue chat-area)
        chat      (str chat (subs from 0 6) ": " content "\n")]
    (f/setValue chat-area chat)))

(defn start []
  (let [rpc-url (-> (g/getElement "rpc-url")
                    (f/getValue))]
    (p/init-protocol
      {:ethereum-rpc-url rpc-url
       :storage          (:storage @state)
       :handler          (fn [{:keys [event-type] :as event}]
                           (log/info "Event:" (clj->js event))
                           (case event-type
                             :new-msg (let [{:keys [from payload]} event
                                            {content :content} payload]
                                        (add-to-chat "chat" from content))
                             :msg-acked (let [{:keys [msg-id]} event]
                                          (add-to-chat "chat" ":" (str "Message " msg-id " was acked")))
                             :initialized (let [{:keys [identity]} event]
                                            (add-to-chat "chat" ":" (str "Initialized, identity is " identity))
                                            (-> (g/getElement "my-identity")
                                                (f/setValue identity)))
                             :delivery-failed (let [{:keys [msg-id]} event]
                                                (add-to-chat "chat" ":" (str "Delivery of message " msg-id " failed")))
                             :new-group-chat (let [{:keys [from group-id]} event]
                                               (add-to-chat "group-chat" ":" (str "Received group chat invitation from " from " for group-id: " group-id)))
                             :group-chat-invite-acked (let [{:keys [from group-id]} event]
                                                        (add-to-chat "group-chat" ":" (str "Received ACK for group chat invitation from " from " for group-id: " group-id)))
                             (add-to-chat "chat" ":" (str "Don't know how to handle " event-type))))})
    (e/listen (-> (g/getElement "msg")
                  (goog.events.KeyHandler.))
      key-handler-events/KEY
      (fn [e]
        (when (= (.-keyCode e) KeyCodes/ENTER)
          (let [msg         (-> (g/getElement "msg")
                                (f/getValue))
                to-identity (-> (g/getElement "to-identity")
                                (f/getValue))]
            (p/send-user-msg {:to      to-identity
                              :content msg})
            (add-to-chat "chat" (p/my-identity) msg)))))))

(defn start-group-chat []
  (let [identities (-> (g/getElement "to-identities")
                       (f/getValue)
                       (s/split "\n"))]
    (add-to-chat "group-chat" ":" (str "Starting group chat with " identities))
    (let [group-id (p/start-group-chat identities)]
      (swap! state assoc :group-id group-id))))

(let [button (g/getElement "connect-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (g/setProperties button #js {:disabled "disabled"})
      (start))))

(let [button (g/getElement "start-group-chat-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (g/setProperties button #js {:disabled "disabled"})
      (g/setProperties (g/getElement "to-identities") #js {:disabled "disabled"})
      (start-group-chat))))

(comment

  (p/init-protocol {:ethereum-rpc-url "http://localhost:4546"
                    :handler          (fn [{:keys [event-type] :as event}]
                                        (log/info "Event:" (clj->js event)))})

  (p/send-user-msg {:to      "0x04a877ae4dcd6005c8f4a576f8c11df56889f5252360cbf7e274bfcfc13f4028f10a3e29ebbb4af12c751d989fbaba09c570a78bc2c5e55773f0ee8579355a1358"
                    :content "Hello World!"})

  (p/my-identity)

  )


(comment



  ;;;;;;;;;;;; CHAT USER 1
  ;(def web3 (p/make-web3 "http://localhost:4546"))
  ;(def user1-ident (p/new-identity web3))
  ;(def listener (p/whisper-listen web3 (fn [err]
  ;                                       (println "Whisper listener caught an error: " (js->clj err)))))
  ;
  ;
  ;(def web3-2 (p/make-web3 "http://localhost:4547"))
  ;(def user2-ident (p/new-identity web3-2))
  ;(p/make-whisper-msg web3-2 user2-ident user1-ident "Hello World!")


  (require '[syng-im.protocol.whisper :as w])
  (def web3 (w/make-web3 "http://localhost:4546"))
  (.newIdentity (w/whisper web3) (fn [error result]
                                   (println error result)))

  )

(comment
  ;;;;;;;;;;;; CHAT USER 2

  (use 'figwheel-sidecar.repl-api)
  (cljs-repl)
  )