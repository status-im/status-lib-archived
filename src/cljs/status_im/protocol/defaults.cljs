(ns status-im.protocol.defaults
  (:require [cljs-time.core :as t]))

(def default-content-type "text/plain")

(def max-send-attempts 5)
(def check-delivery-interval 500)

(def status-message-ttl (* 60 60 2))
(def ack-wait-timeout (t/hours 2))
(def sending-retry-timeout (t/seconds 10))
