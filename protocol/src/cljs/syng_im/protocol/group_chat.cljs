(ns syng-im.protocol.group-chat
  (:require [syng-im.utils.random :as random]
            [syng-im.utils.encryption :refer [new-keypair]]
            [syng-im.protocol.state.group-chat :refer [save-keypair]]
            [syng-im.protocol.state.state :refer [connection my-identity storage]]
            [syng-im.protocol.web3 :refer [make-msg]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]))


