(ns fingertips.state
  (:require [fingertips.core :as core]
            [fingertips.util :refer :all]
            [clojure.set :refer [rename-keys]]
            [clojure.java.io :as io]))

;; command is something like

;; add-collection collection 1
;; leads to an event that then triggers command to add all the cards in his data structure

;; maybe newlines too?
(def clojure-collection-1
  [

   {:question ";; 1. basic destructuring\n(let [... [200 \"hello\"]]\n  {:status status :body body}) ; => {:status 200 :body \"hello\"}", :answer "(let [[status body] [200 \"hello\"]]\n  {:status status :body body}) ; => {:status 200 :body \"hello\"}"}

   {:question ";; 2. destructed nested vectors\n(let [... [[0 1] [2 3]]]\n  {:a a :b b :c c :d d}) ; => {:a 0 :b 1 :c 2 :d 3}", :answer "(let [[[a b] [c d]] [[0 1] [2 3]]]\n  {:a a :b b :c c :d d}) ; => {:a 0 :b 1 :c 2 :d 3}"}

   {:question ";; 3. destructure and keep the map\n(let [... {:a \"foo\" :b \"bar\"}]\n  [a b m]) ; => \"foo\" {:a \"foo\" :b \"bar\"}", :answer "(let [{:keys [a b] :as m} {:a \"foo\" :b \"bar\"}]\n  [a b m]) ; => [\"foo\" \"bar\" {:a \"foo\" :b \"bar\"}]"}

   {:question ";; 4. destructure with variable arguments\n(defn main [...]\n  (apply str args))\n\n(main \"foo\" \"bar\" \"blitz\") ; => \"foobarblitz\"", :answer "(defn main [& args]\n  (apply str args))\n\n(main \"foo\" \"bar\" \"blitz\") ; => \"foobarblitz\""}

   {:question ";; 5. destructure nested string value\n(let [... {:foo {\"bar\" \"quux\"}}]\n  bar) ; => \"quux\"", :answer "(let [{{:strs [bar]} :foo} {:foo {\"bar\" \"quux\"}}]\n  bar) ; => \"quux\""}


   {:question ";; 6. destructure a qualified keyword\n(let [{:keys [...]} {:user/name \"Rich\"}]\n  ...) ; => \"Rich\"" :answer "(let [{:keys [user/name]} {:user/name \"Rich\"}]\n  name) ; => \"Rich\""}

   {:question ";; 7. Destructure with default parameters\n(defn greet [...]\n  (str \"Hi \" name))\n\n(greet {:id 1 :name \"Sara\"}) ; => \"Hi Sara\"\n(greet {:id 2}) ; => \"Hi Nameless\"", :answer "(defn greet [{:keys [name] :or {name \"Nameless\"}}]\n  (str \"Hi \" name))\n\n(greet {:id 1 :name \"Sara\"}) ; => \"Hi Sara\"\n(greet {:id 2}) ; => \"Hi Nameless\""}

   {:question ";; 8. destructure with variable arguments and defauls values\n(defn main [...]\n  (str mode \" \" port))\n\n(main :mode :production :port 8080) ;; => \":production 8080\"\n(main :foo :bar) ;; => \":test 3000\"", :answer ";; destructure with variable arguments and defauls values\n(defn main [& {:keys [mode port]\n               :or {port 3000 mode :test} :as args}]\n  (str mode \" \" port))\n\n(main :mode :production :port 8080) ;; => \":production 8080\"\n(main :foo :bar) ;; => \":test 3000\""}

   {:question ";; 9. ignore parts, pick out a specific position and gather the rest\n(let [... [\"Mr\" \"Willy\" \"Junior\" \"Wonka\" \"Factory\"]]\n  (str \"Hi \" name \" (\" (clojure.string/join \"-\" rest) \")\"))\n;; => \"Hi Willy (Junior-Wonka-Factory)\"", :answer ";; ignore parts, pick out a specific position and gather the rest\n(let [[_ name & rest] [\"Mr\" \"Willy\" \"Junior\" \"Wonka\" \"Factory\"]]\n  (str \"Hi \" name \" (\" (clojure.string/join \"-\" rest) \")\"))\n;; => \"Hi Willy (Junior-Wonka-Factory)\""}

   {:question ";; 10. destructure a bit of everything\n(let [... {:lists [1 5 7 9]}]\n  (str \"first \" first \" rest \" rem)) ; => first 1 rest (5 7 9)", :answer "(let [{[first & rem] :lists} {:lists [1 5 7 9]}]\n  (str \"first \" first \" rest \" rem)) ; => first 1 rest (5 7 9)"}
   ])

(def collections
  {1 clojure-collection-1})

;; SIMPLE STATE MODEL REDUX (maybe)



;; SIMPLE STATE MODEL

;; cards (index card id)
;; id question answer interval scheduled-at deleted session

;; users (index email)
;; email notifications? otp token

;; separate indicies
;; token->cards
;; token->email

;; (sessions NYI)
;; token, browser etc wtf


(defonce db (atom {}))

(defn ensure-file-exists [file]
  (when-not (.exists (io/as-file file))
    (.createNewFile (io/as-file file))))

;; XXX:
;; java.io.FileNotFoundException: ../data/events.log (No such file or directory)
;; (clojure.java.io/resource "foo.css") to access files within your JAR’s classpath and (clojure.java.io/file "foo.json") to access a filepath outside the JAR.
(defn data-dir [filename]
  (str filename)
  #_(str "~/data/fingertips/" filename))

(defn persist-event! [file event]
  (spit (data-dir file) (str event "\n") :append true))

(defn get-events [file]
  (try
    (->> (slurp (data-dir file))
         clojure.string/split-lines
         (mapv read-string))
    (catch Exception e
      (println "Unable to get events, events file empty?")
      [])))

(defn token->cards [{:keys [cards token->cards]} token]
  (->> token
       (get token->cards)
       (select-keys cards)
       ((comp set vals))))

(defn email->cards [state email]
  (->> (get-in state [:users email :user/tokens])
       (map (partial token->cards state))
       (apply clojure.set/union)))

(defn filter-scheduled-cards [cards]
  (->> cards
       (filter #(past? (:card/scheduled-at %)))
       (filter #(not (:card/deleted %)))))

(defn simplify-cards [cards]
  (->> cards
       (map #(select-keys % [:card/id
                             :card/question
                             :card/answer
                             :card/collection]))
       (mapv #(rename-keys % {:card/id :id
                              :card/question :question
                              :card/answer :answer
                              :card/collection :collection}))))

(defn simplify-colls [colls]
  (->> colls
       (mapv #(rename-keys % {:collection/id :id
                              :collection/name :name
                              :collection/cards :cards
                              :collection/public :public}))))

;; XXX: Ugly, replace with as-> and inline?
(defn public-cards->cards []
  (let [state @db]
    (mapv (:cards state) (:public-cards state))))

(defn get-public-cards []
   (simplify-cards (public-cards->cards)))

(defn get-my-collections [token]
  (let [state @db]
    (->> (get-in state [:token->collections token])
         (mapv #(get-in state [:collections %]))
         simplify-colls)))


;; gives a list of Q/a
(defn get-partial-cards-by-collection [coll-id]
  (let [state @db
        card-ids (get-in state [:collections coll-id :cards])]
    (->> (map #(get-in state [:cards %]) card-ids)
         (map #(select-keys % [:card/question :card/answer]))
         simplify-cards)))

(defn get-collection-by-id [coll-id]
  (get-in @db [:collections coll-id]))

;; (get-partial-cards-by-collection "d054b700-a08b-4e72-948a-1778e9347f4f")

;; Get all collections except your own
(defn get-public-collections [token]
  (let [state @db
        user-coll? (or (get-in state [:token->collections token])
                       #{})]
    (->> (:collections state)
         vals
         simplify-colls
         (remove #(user-coll? (:id %)))
         (remove #(empty? (:name %)))
         (remove #(empty? (:cards %))))))

(defn prepare-scheduled-cards [cards]
  (-> cards
      filter-scheduled-cards
      simplify-cards
      shuffle))

(defn get-cards-for-user [token]
  (if-let [email (get-in @db [:token->email token])]
    (email->cards @db email)
    (token->cards @db token)))

(defn get-scheduled-cards [token]
	(let [resp (prepare-scheduled-cards (get-cards-for-user token))]
    ;;(println "ORDER" (map (fn [s] (apply str (take 5 s))) (map :question resp)))
    resp))

(defn get-my-cards [token]
	(->> (get-cards-for-user token)
      (filter #(not (:card/deleted %)))
      simplify-cards))

(defn get-user [email]
  (get-in @db [:users email]))

(defn user-signed-in? [token]
  (get-in @db [:tokens token]))

;; TODO: Make pure with state db
(defn save-event [event]
  (when event
    (persist-event! "events.log" event)
    (swap! db #(core/next-state % event))
    (println "EVENT" (pr-str event))))

(defn load-db [filename]
  (let [_ (ensure-file-exists filename)
        events (get-events filename)]
    (reset! db (reduce core/next-state {} events))))

(defn get-paid-users []
  (set (keys (:paid-users @db))))

(defn get-user-emails []
  (-> @db :users keys vec))

(defn get-opted-in-user-emails []
  (->> @db
       :users
       (remove (fn [[_ {:keys [user/notifications?]}]]
                 (= notifications? false))) ; only explicitly opted out
       (into {})
       keys
       vec))

(defn get-scheduled-cards-count [email]
  (-> (email->cards @db email)
      prepare-scheduled-cards
      count))

(defn get-emails-with-todos []
  (let [emails (get-opted-in-user-emails)]
    (->> (zipmap emails (map get-scheduled-cards-count emails))
         (remove (fn [[_ v]] (= v 0)))
         vec)))

#_(pprint (get-public-collections "dd78f507-4531-41a5-8176-f616bb0b153c")
        )

(comment
  (get-user-emails)
  (get-opted-in-user-emails)

  (-> @db :token->email pprint)

  (-> @db :users (get "me@replaceme.com"))

  (use 'clojure.pprint 'clojure.repl)

  (first (get-events "events.log"))

  (reduce core/next-state {} (get-events "events.log"))

  (def events (get-events "events.log"))

  (-> events pprint)
  (-> @db pprint)

  ;; Use as->:
  (as-> [:foo :bar] v
    (map name v)
    (first v)
    (.substring v 1))

  (pprint (:public-cards @db))
  (get-in @db [:cards "78db519b-7338-47fd-8fe7-03dd48647019"])

  (-> @db :users pprint)

  ;; all cards for email
  (email->cards @db "me@replaceme.com")

  ;; all scheduled cards
  (-> (email->cards @db "me@replaceme.com")
      prepare-scheduled-cards
      count) ;; 11

  (get-emails-with-todos)
  [["foo@replaceme.com" 1] ["bar@replaceme.com" 7] ["me@replaceme.com" 11] ["ot+test1@replacemeoren.com" 2]]

  )
;; TODO: Need to signify if someone is signed in or not.

(defn number-of-cards []
  (count (:cards @db)))

