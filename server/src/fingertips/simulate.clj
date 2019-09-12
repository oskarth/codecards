(ns fingertips.simulate
  ;; XXX: Bad form with refer all so much
  (:require [fingertips.core :refer :all]
            [fingertips.server :refer :all]
            [fingertips.util :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.spec :as s]))

;; Can we simulate commands coming in, handling them and then aggregate sate looking a certain way?

;; bunch of commands, then turn into events
;; next-state it

;; start with just events and next-state

;; COMMAND

(s/valid? :command/client {})
(s/explain :command/client #:command{:type "" :token "" :data {}})
(s/valid? :command/client #:command{:type "" :token "" :data {}})


(defn cmd [type token data]
  {:id (gen-uuid)
   :type type
   :token token
   :data data})

(def c1 (cmd "card" "a" {:id "000" :question "foo" :answer "bar"}))

(defn cmd->ok-event [cmd]
  (let [[status event] (cmd->status+event cmd)]
    (when (= status :ok) event)))

(cmd->ok-event c1)

;; (cmd->status+event)

;; EVENT LEVEL

(defn gen-event [type token data]
  (-> {:event/id (gen-uuid)
       :event/type type
       :event/date-time "2015-01-10T17:33:25.000Z" ;; XXX
       :event/token token}
      (merge data)))

(def events
  [
   ;; Create card
   ((partial gen-event :event/card "a")
    {:card/id "c001"
     :card/question "foo"
     :card/answer "bar"})


   ;; Login etc
   ((partial gen-event :event/register "a")
    {:user/email "me@replaceme.com"})

   ((partial gen-event :event/email-login "a")
    {:user/email "me@replaceme.com"
     :user/otp "000000"})

   ;; Actual command sends encoded-email, encoded-otp and token
   ((partial gen-event :event/auth "a")
    {:user/email "me@replaceme.com"
     :user/otp "000000"
     :user/token "a"})


   ;; Changing collection
   ((partial gen-event :event/collection "b")
    {:collection/id "coll01"
     :collection/name "foobar"})

   ;; ((partial gen-event :event/collection "b")
   ;;  {:collection/id "coll01"
   ;;   :collection/cards #{"c001"}})

   ;; ((partial gen-event :event/collection "b")
   ;;  {:collection/id "coll01"
   ;;   :collection/cards #{"c002" "c003"}})

   ((partial gen-event :event/card "b")
    {:card/id "c002"
     :card/question "coll card"
     :card/answer "coll answer"
     :card/collection "coll01"})


   ])

;; validate events
(remove #(s/valid? :event/event %) events)

(pprint (reduce next-state {} events))

;; properties? should be based on actual operations
(= (-> state (get-in [:cards "c001"]) :card/interval) 0)

;; TODO: Add token to card as owner thing?
;; Wha do I want to check?



(s/valid? :state/state (reduce next-state {} [e1 e2 e3]))
