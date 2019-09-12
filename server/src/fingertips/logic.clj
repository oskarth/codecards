(ns fingertips.logic
  (:require [fingertips.state :refer [db]]))

;; oh?
(-> @db
    :cards
    )

;; TODO: Init interval to 0 when creating a new card
;;(state/save-event [:card (:body req)])
;;(state/save-event (logic/update-review [:card (:body req)]))

;; do you have access to interval here nec?
;; get card from state?
{:id "43b7a1ef-875d-4fd7-9f96-ece7abbd9af8", :response 4, :answer-date "2016-12-26"}

(defn update-review [id]
  )

;; huh data layer man, keep screwing up cause no conform to ze spec
;; easiest way to introduce it? just update to alpha and lets rock?
;; all must be events, and cards. simple!

(:cards @db)
(get (:cards @db) "43b7a1ef-875d-4fd7-9f96-ece7abbd9af8")

#_(defn next-review [answer-date response interval]
  (let [offset (calc-offset response interval)])
  ;; new-date = answer-date + offset
  )

;; repsonse 0 is delete
#_(defn calc-offset [response interval]
  (cond (= response 1) interval
        (= response 2)
        (= response 3)
        (= response 4)

)
  )

;; Pimsleur interval
;; Either advance one ring, fall back one, or fall back to start
;; response, interval

;; can just map both to fall back to start
;; and top to advance

;; BOXES
{1 "1d"
 2 "5d"
 3 "25d"
 4 "4m" ;; 125d
 5 "2y"} ;; 625d

;; how do we calculate next date etc?
;; Basic Leitner system

;; Why not? Very basic
;; 1d, 3d, 10d, 30d, 90d, 270d

;; lets start over with state shall we

;; Pimsleur high end scheme
;; 1d, 5d, 25d, 4m, 2y - very simple!
;; 5h
;; what am I using the boxes for then?
;;  4 levels.
;; Forgot = back to box 1
;; Got it = +1 level
;; So-so = Add back to review with -1 level?

;;The intervals published in Pimsleur's paper were: 5 seconds, 25 seconds, 2 minutes, 10 minutes, 1 hour, 5 hours, 1 day, 5 days, 25 days, 4 months, and 2 years.
;; x5 all the time ish


;; actually still no make sense
;; try now

;; ok would be nice to actually get reviewable thing
;; time to bed though, bah

;; Tomorrow:

;; 1. Write logic to get cards in a reviewable state
;; 2. Integrate Code Mirror
;; 3. Actually add content
