(ns status-im.protocol.defaults
  (:require [cljs-time.core :as t]))

(def default-content-type "text/plain")

(def max-retry-send-count 5)
(def ack-wait-timeout-ms (t/millis 5000))
(def check-delivery-interval-msg 100)
