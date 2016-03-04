(ns cljs-tests.core
  (:require [cljs-tests.protocol.api :as p]
            [cljs-tests.utils.logging :as log]
            [goog.dom :as g]
            [goog.dom.forms :as f]
            [goog.events :as e]
            [goog.events.EventType]
            [goog.events.KeyCodes]
            [goog.events.KeyHandler]
            [goog.events.KeyHandler.EventType :as key-handler-events])
  (:import [goog.events EventType]
           [goog.events KeyCodes]))

(enable-console-print!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defn start []
  (let [rpc-url (-> (g/getElement "rpc-url")
                    (f/getValue))
        {identity :identity}
        (p/init-protocol
          {:ethereum-rpc-url rpc-url
           :handler          (fn [{:keys [from payload event-type] :as event}]
                               (log/info "Event:" (clj->js event))
                               (case event-type
                                 :new-msg (let [{content :content} payload
                                                chat-area (g/getElement "chat")
                                                chat      (f/getValue chat-area)
                                                chat      (str chat (subs from 0 5) ": " content "\n")]
                                            (f/setValue chat-area chat))
                                 (log/info "Don't know how to handle" (clj->js event-type))))})]
    (-> (g/getElement "my-identity")
        (f/setValue identity))
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
                              :content msg})))))))

(let [button (g/getElement "connect-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (g/setProperties button #js {:disabled "disabled"})
      (start))))


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

  )

(comment
  ;;;;;;;;;;;; CHAT USER 2

  (use 'figwheel-sidecar.repl-api)
  (cljs-repl)
  )