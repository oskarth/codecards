(ns fingertips.server
  (:require [org.httpkit.server :as server]
            [ring.middleware.params :as rmparams]
            [ring.middleware.json :as rmjson]
            [ring.middleware.cookies :as cookies]
            [clj-time.core :as time]
            [ring.util.response :refer [response]]
            [clojure.spec :as s]
            [bidi.ring :as bring]
            [fingertips.state :as state]
            [fingertips.util :refer :all]
            [clj-mailgun.core :as mailgun]
            [clojure.string :as str]
            [chime :refer [chime-at]]
            [clj-time.periodic :refer [periodic-seq]])
  (:gen-class))

;; KISS Config
(def app-env (System/getenv "APP_ENV"))
(def client-url (System/getenv "CLIENT_URL"))
(def api-url (System/getenv "API_URL"))
(def mailgun-base-url (System/getenv "MAILGUN_BASE_URL"))
(def mailgun-api-key (System/getenv "MAILGUN_API_KEY"))

(declare handle-event!)
(declare handle-event-and-respond!)
(declare send-reminder-email)
(declare send-report-email)
(declare send-dead-code-email)

(defn assert-mailgun-api-key []
  (assert ((complement nil?) mailgun-api-key) "Need a valid API key!"))

;; Only different with "new" card is that we don't use the response.
;; So what do we do about it?
(defn compact-qualified-card [{:keys [id question answer response]}]
  (->> {:card/id        id
        :card/question  question
        :card/answer    answer
        :card/response  response}
       (remove (fn [[_ v]] (nil? v)))
       (into {})))

;; XXX: Consider adding deleted (and hidden) here too.
(defn qualified-card [{:keys [id question answer collection]}]
  (->> {:card/id          id
        :card/question    question
        :card/answer      answer
        :card/collection  collection}
       (remove (fn [[_ v]] (nil? v)))
       (into {})))

(defn qualified-review [{:keys [id response]}]
  {:card/id        id
   :card/response  response})

(defn compact-qualified-user
  [{:keys [email otp encoded-email notifications?
           encoded-otp token cards-due]}]
  (->> {:user/email              (or email (decode-base64 encoded-email))
        :user/notifications?     notifications?
        :user/otp                (or otp (decode-base64 encoded-otp))
        :user/token              token
        :user/cards-due          cards-due}
       (remove (fn [[_ v]] (nil? v)))
       (into {})))

(defn compact-qualified-payment
  [{:keys [email id livemode amount]}]
  (->> {:user/email              email
        :payment/id              id
        :payment/livemode        livemode
        :payment/amount          amount}
       (remove (fn [[_ v]] (nil? v)))
       (into {})))

;; XXX: differentiate between no name and change name?
(defn compact-qualified-collection
  [{:keys [id name cards public]}]
  (->> {:collection/id id
        :collection/name name
        :collection/cards cards
        :collection/public public}
       (remove (fn [[_ v]] (nil? v)))
       (into {})))

(defn new-event [type token]
  {:event/id (gen-uuid)
   :event/date-time (now)
   :event/type (keyword "event" type)
   :event/token token})

;; Kill list:
;; Feb 15, compact-qualified-card (staging though)
(defn deadf [f]
  (let [s (str "SHOULD BE DEAD:" (str f))]
    (send-dead-code-email s)
    (println s))
  f)

(def user-commands #{"register" "email-login" "auth" "email-reminder" "update-settings"})

(defn data->event-data [type data]
  (let [f (condp contains? type
            #{"card"} qualified-card
            #{"review"} qualified-review
            #{"collection"} compact-qualified-collection
            #{"new-card" "review-card"}  (deadf compact-qualified-card)
            user-commands                compact-qualified-user
            #{"checkout"}                compact-qualified-payment
            ;; Is this a collection?
            #{"add-collection"}          compact-qualified-collection
            identity)]
    (f data)))

(defn email-reminder-command [email cards-due]
  {:id (gen-uuid)
   :type "email-reminder"
   :token "0xdeadbeef"
   :data {:email email :cards-due cards-due}})

(defn cmd->unvalidated-event [{:keys [type token data] :as cmd}]
  (merge (new-event type token) (data->event-data type data)))

(defn explain-event [event]
  (pr-str (s/explain-data :event/event event)))

;; XXX: Hardcoded for now
;; XXX: Doesn't actually work because of lack
(def email-whitelist
  #{"me@replaceme.com"
    })

;; TODO: And todays day?
;; If register event, must've paid or be on The List.
(defn authenticated?
  [{email' :user/email otp' :user/otp token' :user/token :as event}]
  (let [{:keys [user/email user/otp user/token]} (state/get-user email')
        auth?     (= (:event/type event) :event/auth)
        register? (= (:event/type event) :event/register)]
    (cond auth?     (and email otp   (= email' email) (= otp' otp))
          register? (or (contains? email-whitelist email')
                        (contains? (state/get-paid-users) email'))
          ;; XXX: This branch will never happen, only auth and register
          :else     (and email token (= email' email) (= token' token)))))

;; Need url encode or md5 or something
;; https://codecards.me#auth?e=XXX
;; ok it lower cases, wow. omg.
;; (decode-base64 "OTQzMzcz")
;; WOW
;; (decode-base64 "otqzmzcz")


;; XXX: Security flaw, think anyone can auth? :S E.g. update-settings with different token. No?
(defn cmd->status+event [cmd]
  (println "COMMAND" (pr-str cmd))
  (if (s/valid? :command/client (qualify-map "command" cmd))
    (let [event (cmd->unvalidated-event cmd)
          bad? (not (s/valid? :event/event event))
          access? (if (contains? #{:event/register :event/auth} (:event/type event))
                    (authenticated? event)
                    true)]
      (cond bad?    [:bad-command (explain-event event)]
            access? [:ok event]
            :else   [:access-denied (pr-str event)]))
    (do (println "INVALID COMMAND" cmd)
      [:bad-command cmd])))

(defn send-reminder! [email count]
  (let [cmd (email-reminder-command email count)
        [status event] (cmd->status+event cmd)]
    (println "SENDING REMINDER: " email count)
    (send-reminder-email email count)
    (handle-event! event)))

(defn send-reminders! []
  (let [email-todos (state/get-emails-with-todos)]
    (println "SEND REMINDERS:" email-todos)
    (send-report-email email-todos)
    (doseq [[email count] email-todos]
      (send-reminder! email count))))

(defn periodic-reminders! []
  (println "STARTED PERIODIC-REMINDERS!")
  (chime-at (periodic-seq (.. (time/now)
                              (withTime 12 0 0 0))
                          (-> 1 time/days))
            (fn [time]
              (println "Chiming at" time)
              (send-reminders!))))

(defn assoc-cors-headers [resp]
  (-> resp
      (assoc-in [:headers "Access-Control-Allow-Origin"] client-url)))

(defn token-url [email token]
  (str client-url "#auth?e=" (encode-base64 email) "&t=" (encode-base64 token)))

(defn send-actual-email [email text]
  (let [credentials {:api-key mailgun-api-key :domain mailgun-base-url}
        params {:from "me+codecards@replaceme.com"
                :to email
                :subject "Code Cards login"
                :text text}
        resp (mailgun/send-email credentials params)]
    (println "EMAIL ATTEMPT:" params "STATUS:" (:status resp))
    (= 200 (:status resp))))

;; TODO: Prettyify with link and utm
(defn send-reminder-email [email count]
  (let [credentials {:api-key mailgun-api-key :domain mailgun-base-url}
        text (str "Hi there!"
                  "\n\n"
                  "You have " count " cards due. Head over to https://codecards.me to review them."
                  "\n\n"
                  "If you no longer wish to receive reminders, you can turn them off under \"Settings\" at https://codecards.me. If you just want to receive them less frequently, please reply to this email."
                  "\n\n"
                  "Happy learning!"
                  "\n"
                  "- Oskar from Code Cards")
        params {:from "me+codecards@replaceme.com"
                :to email
                :subject (str "Code Cards - " count " cards due")
                :text text}
        resp (mailgun/send-email credentials params)]
    (println "EMAIL ATTEMPT:" params "STATUS:" (:status resp))
    (= 200 (:status resp))))

(defn send-payment-email [text]
  (let [credentials {:api-key mailgun-api-key :domain mailgun-base-url}
        params {:from "me+codecards@replaceme.com"
                :to "me@replaceme.com"
                :subject "Code Cards payment"
                :text text}
        resp (mailgun/send-email credentials params)]
    (println "EMAIL ATTEMPT:" params "STATUS:" (:status resp))
    (= 200 (:status resp))))

(defn send-dead-code-email [text]
  (let [credentials {:api-key mailgun-api-key :domain mailgun-base-url}
        params {:from "me+codecards@replaceme.com"
                :to "me@replaceme.com"
                :subject "Code Cards Dead Code"
                :text text}
        resp (mailgun/send-email credentials params)]
    (println "EMAIL ATTEMPT:" params "STATUS:" (:status resp))
    (= 200 (:status resp))))

(defn send-report-email [text]
  (let [credentials {:api-key mailgun-api-key :domain mailgun-base-url}
        params {:from "me+codecards@replaceme.com"
                :to "me@replaceme.com"
                :subject "Code Cards Reminders sent"
                :text text}
        resp (mailgun/send-email credentials params)]
    (println "EMAIL ATTEMPT:" params "STATUS:" (:status resp))
    (= 200 (:status resp))))

(defn login-email-text [email token]
  (str "Login here: " (token-url email token)))

;; XXX: A bit strange that we send send email before the command, no?
(defn send-email! [{:keys [user/email]}]
  (let [token (otp)
        cmd {:id (gen-uuid)
             :type "email-login"
             :token token
             :data {:email email :otp token}}
        success? (send-actual-email email (login-email-text email token))
        [status event] (cmd->status+event cmd)]
    (if (and success? (= status :ok))
      (do (println "Login email sent to" email)
          (state/save-event event))
      (println "No email sent" success? "or bad command" status (pr-str cmd)))))


;; TODO: Eval
(defn curl-charge [payment-token]
  (str "curl https://api.stripe.com/v1/charges -u sk_test_BREPLACEMEWITHREALKEYQQQ: -d amount=1000 -d currency=usd -d description=\"Code Cards - one-time fee\" -d source=" payment-token))

;; XXX: This should ideally call Stripe and stuff
;; Start with manually? Just email Y.T.
(defn checkout! [event]
  (println "CHECKOUT" (pr-str event))
  (send-payment-email
   (str (pr-str event)
        "\n\n\n"
        (curl-charge (:payment/id event)))))

(defn new-card-command [token question answer collection]
  {:id (gen-uuid)
   :type "card"
   :token token
   :data {:id (gen-uuid)
          :question question
          :answer answer
          :collection collection}})

(defn new-collection-command [token coll-id coll-name]
  {:id (gen-uuid)
   :type "collection"
   :token token
   :data {:id coll-id
          :name coll-name}})

;; XXX: Ugly with so many lets...
(defn add-collection! [{:keys [collection/id event/token] :as event}]
  (println "ADD CARDS AS EVENTS" (pr-str event))
  (let [new-coll-id (gen-uuid)
        coll-name (:collection/name (state/get-collection-by-id id))
        partial-cards (state/get-partial-cards-by-collection id)]

    (println "Adding collection" new-coll-id)
    (let [coll-cmd (new-collection-command token new-coll-id coll-name)
          [status event] (cmd->status+event coll-cmd)
          _ (println (pr-str status) (pr-str event))]
      (if (= status :ok)
        (handle-event-and-respond! event nil)
        (println "UNABLE TO CREATE EVENT")))

    (doseq [{:keys [question answer]} partial-cards]
      (println "Adding question" question)
      (let [cmd (new-card-command token question answer new-coll-id)
            [status event] (cmd->status+event cmd)
            _ (println (pr-str status) (pr-str event))]
        (if (= status :ok)
          (handle-event-and-respond! event nil)
          (println "UNABLE TO CREATE EVENT"))))))

;; add a new collection with same name
;; clone cards (same Q and Q)


(defn print-new-card! [{:keys [:card/question :card/answer]}]
  (println "QUESTIOANSWER")
  (println (pr-str {:question question :answer answer}))
  )

;; Only happens once when coming from client
;; XXX: Consider what should be going on here for card/review?
(defn side-effects! [{:keys [event/type] :as event}]
  (condp = type
    :event/register (send-email! event)
    :event/checkout (checkout! event)
    :event/add-collection (add-collection! event)
    :event/new-card (print-new-card! event)

    nil))

(defn handle-event! [event]
  (state/save-event event)
  (side-effects! event))

;; NOTE: Only happens when client triggers an event, not when we load events.
(defn handle-event-and-respond! [event body]
  (state/save-event event)
  (side-effects! event)
  {:status 200 :body (:command-id body)})

(defn post-command-handler [{:keys [body] :as req}]
  (let [_ (println "POST-COMMAND-HANDLER" body)
        [status event] (cmd->status+event body)
        _ (println (pr-str status) (pr-str event))
        resp (cond (= status :ok) (handle-event-and-respond! event body)
                   (= status :access-denied) {:status 403 :body (:command-id body)}
                   :default {:status 400 :body (:command-id body)})]
    (-> resp
        (assoc-in [:headers "Access-Control-Allow-Origin"] client-url))))

;; TODO: Dedup header assocs
(defn get-cards-handler [{{:strs [authorization]} :headers :as req}]
  (-> {:status 200 :body (state/get-scheduled-cards authorization)}
      (assoc-in [:headers "Access-Control-Allow-Origin"] client-url)
      (assoc-in [:headers "Access-Control-Allow-Credentials"] true)))

(defn get-my-cards-handler [{{:strs [authorization]} :headers :as req}]
  (-> {:status 200 :body (state/get-my-cards authorization)}
      (assoc-in [:headers "Access-Control-Allow-Origin"] client-url)
      (assoc-in [:headers "Access-Control-Allow-Credentials"] true)))

;; TODO: Add collections and my collections
(defn get-state-handler [{{:strs [authorization]} :headers :as req}]
  (-> {:status 200
       :body {:my-cards           (state/get-my-cards authorization)
              :my-collections     (state/get-my-collections authorization)
              :public-collections (state/get-public-collections authorization)
              :scheduled          (state/get-scheduled-cards authorization)
              :public-cards       (state/get-public-cards)}}
      (assoc-in [:headers "Access-Control-Allow-Origin"] client-url)
      (assoc-in [:headers "Access-Control-Allow-Credentials"] true)))

(defn get-public-cards-handler [req]
  (let [resp {:status 200 :body (state/get-public-cards)}]
    (-> resp
        (assoc-in [:headers "Access-Control-Allow-Origin"] client-url)
        (assoc-in [:headers "Access-Control-Allow-Credentials"] true))))

(defn status-check [req]
  (do (println "STATUS CHECK")
      {:status 200 :body (str (state/number-of-cards))}))

(defn cors-preflight-handler [req]
  (-> {:status 200 :body "ok"}
      (assoc-in [:headers "Access-Control-Allow-Origin"] client-url)
      (assoc-in [:headers "Access-Control-Allow-Methods"] "POST")
      (assoc-in [:headers "Access-Control-Allow-Headers"] "Content-Type, Authorization")
      (assoc-in [:headers "Access-Control-Allow-Credentials"] true)))

;; TODO: Remove / disable GET for cards / public-cards / my-cards (using state now)
(def routes
  ["/" {"api/cards" {:options cors-preflight-handler
                     :get get-cards-handler
                     :post post-command-handler}
        "api/public-cards" {:options cors-preflight-handler
                            :get get-public-cards-handler}
        "api/my-cards" {:options cors-preflight-handler
                        :get get-my-cards-handler}
        "api/state" {:options cors-preflight-handler
                     :get get-state-handler}
        "api/status" status-check}])

(def app
  (-> routes
      bring/make-handler
      rmparams/wrap-params
      rmjson/wrap-json-response
      cookies/wrap-cookies
      (rmjson/wrap-json-body {:keywords? true :bigdecimals? true})))

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))

(defn start-server [& [opts]]
  (reset! server (server/run-server #'app (or opts {:port 8084}))))

(defn -main [& args]
  (start-server))

(defn run []
  ;; Reload data and restart server automatically when evaluating namespace
  (println "Loading DB...")
  (state/load-db "events.log")
  (stop-server)
  (assert-mailgun-api-key)
  (when ((complement nil?) mailgun-api-key)
    (println "Starting server..." app-env)
    (start-server)))

(defonce cancel-reminders-fn (periodic-reminders!))


(defn -main [& args]
  (run))

(run)

(comment
  ;; REMINDERS SENT
  ;; Feb 2, 18.07
  ;; Feb 3, 19.35
  ;; Feb 4, 22.16
  ;; Feb 5, 20.01
  ;; Feb 6, 19.01
  ;; Feb 7, 22:13
  ;; Feb 8, 23:26
  ;; Feb 9, 22:53
  ;; WARNING: This will send live emails to people!
  ;(send-reminders!)



  ;; XXX: Security flaw!
  ;; manual-cmd
  #_(let [[status event] (-> {:id (gen-uuid)
                           :type "update-settings"
                           :token "0xdeadbeef" ;; XXX: Security flaw! Anyone can do this
                           :data {:encoded-email (encode-base64 "XXX@EMAIL.COM")
                                  :notifications? false}}
                          cmd->status+event)]
    (handle-event! event))
  )
