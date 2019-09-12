(ns fingertips.core
  (:require [clojure.spec :as s]
            [clojure.spec.gen :as gen]
            [clojure.set :refer [union]]
            [fingertips.util :refer :all]
            [clj-time.core :as time]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]))

;; TODO: Idempotent reviews. If multiple are posted we get NONSENSE.
;; TODO: Show if we are logged in.

(defn sample-date-gen []
  (-> (comp str #(time/plus (time/date-time 2015 01 01)
                            (time/days %)
                            (time/seconds (rand-int 86400))))
      (map (range 700))
      set
      s/gen))

(s/def ::date-time
  (s/with-gen
    (s/spec (s/and string? date-time?))
    sample-date-gen))

;; really 8-4-4-4-12, and generators...
;;(s/def ::uuid (and string? #(> (count %) 3)))

(s/def :event/id string?)
(s/def :event/type keyword?)
(s/def :event/token string?) ;; XX :session/token?

(s/def :event/date-time ::date-time)

(s/def :card/id string?)
(s/def :card/question string?)
(s/def :card/answer string?)
(s/def :card/deleted boolean?)
(s/def :card/response (and number? (s/int-in 0 5)))
(s/def :card/schedule-at ::date-time)
(s/def :card/collection string?)

(s/def :user/id string?)
(s/def :user/email string?)
(s/def :user/notifications? boolean?)
(s/def :user/otp string?)
(s/def :user/time-to-live ::date-time)
(s/def :user/encoded-email string?)
(s/def :user/encoded-otp string?)
(s/def :user/tokens set?)
(s/def :user/cards-due number?) ;; XXX: This changes

;; XXX Change to uuid later?
;;(s/def :collection/id number?) ;; XXX: used to be a number! Horrible!
;; Will break clojure collection, gah
(s/def :collection/id (or string? number?)) ;; XXX

(s/def :collection/name string?)
(s/def :collection/cards set?) ;; XXX: I think?
(s/def :collection/public boolean?)

(s/def :command/type string?)
(s/def :command/token string?)
(s/def :command/data map?)
(s/def :command/client
  (s/keys :req [:command/type :command/token :command/data]))

(s/def :event/base (s/keys :req [:event/id
                                 :event/type
                                 :event/date-time
                                 :event/token]))

(defmulti event-type :event/type)

;; MISC NOTES:
;; new-card, review-card, schedule-card, register, update-settings,
;; email-login, email-reminder, auth, check, add-collection

;; don't know if we actually use schedule-card?
;; how would we version events?

;; I want to introduce these two events:
;; card, review

;; NEW CARD is just id q a
;; "EDIT" CARD is just same id and change q and a
;;
;; REVIEW is a card-id and response - thats it

;; We are changing the concept of a CARD though. Are we not?
;; Maybe not base type but yeah
;; Ok lets just try this

;; Also card OWNER and card FORK

;; Will have to copy over prod db and see if it seems reasonable

;; Feature flag?

;; Should we not have user ids rather than just emails?
;; Later though.

;; If no change event stream, maybe add flag like forkable or something
;; that can never be changed.

;; Got this event now
;; {:event/id "187f47f6-064f-48bd-a73b-48d56fbe80de", :event/date-time "2017-02-12T05:10:08.313Z", :event/type :event/card, :event/token "0b13b0a5-0d90-4c9e-97f8-581c1f353846", :card/id "7b3bcd20-37f7-4609-a2c1-b2573df70173", :card/question "foo", :card/answer "bar"}

;; New card event
(defmethod event-type :event/card [_]
  (s/merge :event/base
           (s/keys :req [:card/id
                         :card/question
                         :card/answer]
                   :opt [:card/collection])))

;; New review event
;; XXX: Not sure about card response
(defmethod event-type :event/review [_]
  (s/merge :event/base
           (s/keys :req [:card/id
                         :card/response])))

;;;;;;;;;;;;;;;;;;;;;;;;;

(defmethod event-type :event/new-card [_]
  (s/merge :event/base
           (s/keys :req [:card/id
                         :card/question
                         :card/answer])))

(defmethod event-type :event/review-card [_]
  (s/merge :event/base
           (s/keys :req [:card/id
                         :card/response])))

;; XXX: Do we actually use this event type?
(defmethod event-type :event/schedule-card [_]
  (s/merge :event/base
           (s/keys :req [:card/id
                         :card/question
                         :card/answer
                         :card/schedule-at])))

(defmethod event-type :event/register [_]
  (s/merge :event/base
           (s/keys :req [:user/email]
                   :opt [:user/notifications?])))

(defmethod event-type :event/update-settings [_]
  (s/merge :event/base
           (s/keys :req [:user/notifications?])))

(defmethod event-type :event/email-login [_]
  (s/merge :event/base
           (s/keys :req [:user/email
                         :user/otp])))

(defmethod event-type :event/email-reminder [_]
  (s/merge :event/base
           (s/keys :req [:user/email
                         :user/cards-due])))

(defmethod event-type :event/auth [_]
  (s/merge :event/base
           (s/keys :req [:user/email
                         :user/token]
                   :opt [:user/otp])))

(defmethod event-type :event/checkout [_]
  (s/merge :event/base
           (s/keys :req [:user/email
                         :payment/id
                         :payment/livemode
                         :payment/amount])))

;; XXX: Bad name... I mean, maybe? but yeah.
(defmethod event-type :event/add-collection [_]
  (s/merge :event/base
           (s/keys :req [:collection/id])))

(defmethod event-type :event/collection [_]
  (s/merge :event/base
           (s/keys :req [:collection/id]
                   :opt [:collection/name
                         :collection/cards
                         :collection/public])))

(s/def :event/event (s/multi-spec event-type :event/type))

;; Card state that we track
(s/def :state/card (s/keys :req [:card/id
                                 :card/question
                                 :card/answer
                                 :card/interval
                                 :card/scheduled-at
                                 :card/deleted]))

(s/def :state/state (s/map-of :card/id :state/card))

;; Core logic

(defn schedule-card [date-str interval response]
  (assert response "response empty, can't schedule card")
   (let [first-review? (= interval 0)
        new-interval (cond (and first-review? (= response 2)) 1
                           (and first-review? (= response 3)) 1
                           (and first-review? (= response 4)) 1
                           (= response 1) 0
                           (= response 2) 1
                           (= response 3) (min (* interval 3) 10000)
                           (= response 4) (min (* interval 5) 10000)
                           :else interval)
        scheduled-at (-> (tf/parse (tf/formatter :date-time) date-str)
                         (time/plus (time/days new-interval))
                         str)]
    {:card/interval     new-interval
     :card/scheduled-at scheduled-at
     :card/deleted      (= response 0)}))

(defn schedule-new-card [date-str]
  (let [new-interval 0
        scheduled-at (-> (tf/parse (tf/formatter :date-time) date-str)
                         (time/plus (time/days new-interval))
                         str)]
    {:card/interval     new-interval
     :card/scheduled-at scheduled-at
     :card/deleted      false}))

;; XXX: Hardcoded for now, should probably be attribute of cards or something
(def public-cards
  #{
    ;; from live cards
    "78db519b-7338-47fd-8fe7-03dd48647019" ;; destructure nested map
    "7aa716b6-08ed-4537-b02f-c0ee26cf8e43" ;; foo+i@bar.com to file
    "55a06e18-e24a-4681-856e-3ac7668db63c" ;; make PATH readable
    })

(defn maybe-public-card [db id]
  (if (contains? public-cards id)
    (update db :public-cards #(union % #{id}))
    db))

(defn maybe-add-card-to-collection [db card-id collection-id]
  (if collection-id
    (update-in db [:collections collection-id :cards]
               #(union % #{card-id}))
    db))

(defn new-card [db {:keys [card/id event/date-time event/token] :as e}]
  (let [card (-> (select-keys e [:card/id :card/question :card/answer])
                 (merge (schedule-new-card (:event/date-time e))))]
    (-> db
        (assoc-in [:cards id] card)
        (update-in [:token->cards token] #(union % #{id}))
        (maybe-public-card id))))

;; XXX: What happens when we edit a card?
(defn upsert-card [db {:keys [card/id event/date-time event/token] :as e}]
  (let [card (-> (select-keys e [:card/id
                                 :card/question
                                 :card/answer
                                 :card/collection])
                 (merge (schedule-new-card (:event/date-time e))))]
    (-> db
        (assoc-in [:cards id] card)
        (update-in [:token->cards token] #(union % #{id}))
        (maybe-public-card id)
        (maybe-add-card-to-collection id (:card/collection e)))))

(defn review-card [db {:keys [card/id event/date-time card/response]}]
  (let [merge (fn [{:keys [card/interval] :as old}]
                 (merge old (schedule-card date-time interval response)))]
    (update-in db [:cards id] merge)))

(defn register [current new]
  (-> current
      (merge (select-keys new [:user/email :user/notifications?]))))

(defn update-settings [current new]
  (-> current
      (merge (select-keys new [:user/notifications?]))))

(defn login [current new]
  (-> current
      (merge (select-keys new [:user/email :user/otp]))))

;; XXX: user/token / user/tokens confusion? we'll see.
(defn auth
  "Authenticates a user by adding their token to that user,
   and associating the email with that session token."
  [db {:keys [user/email event/token] :as event}]
  (let [merge-fn #(if (set? %1) (union %1 %2) %2)
        new-user {:user/email email :user/tokens #{token}}]
    (-> db
        (update-in [:users email] #(merge-with merge-fn % new-user))
        (assoc-in [:token->email token] email))))

;; Assume paid
(defn checkout [db {:keys [user/email]}]
  (-> db
      (assoc-in [:paid-users email] true)))

(defn merge-with-union [x y]
  (let [merge-fn #(if (set? %1) (union %1 %2) %2)]
    (merge-with merge-fn x y)))


(defn collection
  [db {:keys [collection/id event/token] :as e}]
  (let [coll (select-keys e [:collection/id
                             :collection/name
                             :collection/cards
                             :collection/public])]
    (-> db
        (update-in [:collections id] #(merge-with-union % coll))
        (update-in [:token->collections token] #(union % #{id})))))

(defn next-state
  [db {:keys [event/type event/token card/id user/email] :as event}]
  (condp = type
    :event/card            (upsert-card db event)
    :event/review           (review-card db event)
    :event/new-card        (new-card db event)
    :event/review-card     (review-card db event)
    :event/collection      (collection db event)
    :event/register        (update-in db [:users email] #(register % event))
    :event/update-settings (update-in db [:users email] #(update-settings % event))
    :event/email-login     (update-in db [:users email] #(login % event))
    :event/auth            (auth db event)
    :event/checkout        (checkout db event)
    db))

;; Examples
(comment

  (s/valid? :event/event {:event/type :event/new-card
                          :event/id "foo"
                          :event/date-time (str (time/now))
                          :card/id "bar"
                          :card/question "foo"
                          :card/answer "bar"})

  (s/valid? :event/event {:event/type :event/review-card
                          :event/id "foo"
                          :event/date-time (str (time/now))
                          :card/id "bar"
                          :card/response 0})

  (s/valid? :event/event {:event/type :event/register
                          :event/id "foo"
                          :event/date-time (str (time/now))
                          :user/email "foo@bar.com"
                          :user/notifications? true})

  (first (gen/sample (s/gen :event/event)))

  (def e1 {:event/id "e001", :event/type :event/new-card, :event/date-time "2015-01-10T17:33:25.000Z", :card/id "c001", :card/question "foo", :card/answer "bar"})
  (def e2 {:event/id "e002", :event/type :event/new-card, :event/date-time "2015-01-12T17:33:25.000Z", :card/id "c002", :card/question "quux", :card/answer "baz"})
  (def e3 {:event/id "e003", :event/type :event/review-card, :event/date-time "2015-01-11T17:33:25.000Z", :card/id "c001", :card/response 3})

  (reduce next-state {} [e1 e2 e3])

  (s/valid? :state/state (reduce next-state {} [e1 e2 e3]))
  )
