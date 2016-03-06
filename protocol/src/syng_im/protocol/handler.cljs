(ns syng-im.protocol.handler
  (:require [syng-im.protocol.state.state :as state]))

(defn invoke-handler [event-type params]
  ((state/handler) (assoc params :event-type event-type)))
