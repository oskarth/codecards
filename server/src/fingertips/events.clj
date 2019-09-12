(ns fingertips.events)


;; I have no idea why I find this to be so difficult. it is ridic.

;; Attempt at modelling what the hell is going on.

;; Events: event/new-card, event/review-card, event/schedule-card
;; Commands: command/new-card, command/review-card, command/schedule-card
;; State: consequence of events. Map of multiple relevant kv pairs.


(def c1 {:command/type :command/new-card :card/id "1" :card/question "foo" :card/answer "bar"}) ;; =>
(def e1 {:event/type :event/new-card :card/id "1" :card/question "foo" :card/answer "bar"})

(defn persist-event [e]
  (println "PERSIST" e)
  e)

;; assoc id, date-time, persist and send to handle-event channel
;; publish to multiple places that listen
(defn event [e]
  (-> e
      persist-event
      update-cards
      handle-event))

;;" Aggregates that handles Commands and generates Events based on the current state "
;; Application services that receives Commands and routes it to the appropriate aggregate

(defn card-timeline [state {:keys [card/id event/type] :as event}]
  (-> state
      (assoc id (or (get state id) []))
      (update id #(conj % type))))

(defn update-cards [e]
  (swap! db #(next-state % event)))

;; process commands (can read aggregate) -> events
;; event -> event queue -> event store
;;                      -> aggregate aggregate
;;                      -> event listeners -> commands

;; commands -> handle-commands (can read db) -> events
;; event -> event store / db / listeners
;; listeners -> commands

;; Why don't you start with use cases and go from there?
;; You almost had something! WTH happened with that?


;; What is the minimum info needed?
;; And why don't you just start with what you got?

(defn generate-schedule-card-event [db card-id]
  {:event/type       :event/review-card
   :card/id          card-id
   ;; look these up in db
   :card/question 1
   :card/answer      (:card/answer cmd)
   :card/schedule-at (:card/schedule-at cmd)})

;; TODO: Validation, authentication, etc.
(defn handle-command [db {:keys [command/type] :as cmd}]
  (condp = type
    :command/new-card      (event {:event/type       :event/new-card
                                   :card/id          (:card/id cmd)
                                   :card/question    (:card/question cmd)
                                   :card/answer      (:card/answer cmd)})
    :command/review-card   (event {:event/type       :event/review-card
                                   :card/id          (:card/id cmd)
                                   :card/response    (:card/response cmd)})
    :command/schedule-card (event (generate-schedule-card-event db (:card/id cmd)))))

;; Why is this difficult Oskar?

;; Command to schedule a specific command can influence aggregate?
;; does that create event?

;; Commands can be rejected.
;; Events have happened.

;; Yes, I would say primarily in dispatching. Commands are dispatched to a single handler, but events are dispatched to multiple listeners. Granted, the implementation differences are in the buses, but I still use separate event and command interfaces, so that each bus only takes the messages it can. – quentin-starin Feb 11 '11 at 16:39

;; Client issuing a command -> Command -> Command Handler -> Event -> Event Listeners/Projections

;; You should not raise event from event handler - just don't do it! You should use sagas instead.
;; Event listener that listens for reviewable cards and issues scheduling commands?


;; events dispatched to multiple listeners

(defn reviewable? [type]
  (or (= type :event/new-card) (= type :event/review-card)))

(defn handle-event [e]
  (cond (reviewable? (:event/type e)) [:schedule-cmd (:card/id e)]
        :else nil))

(handle-command c1)
