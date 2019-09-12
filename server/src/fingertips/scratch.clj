;; 1 basic destructuring
(let [... [200 "hello"]]
  {:status status :body body}) ; => {:status 200 :body "hello"}

(let [[status body] [200 "hello"]]
  {:status status :body body}) ; => {:status 200 :body "hello"}


;; 2. destructed nested vectors
(let [... [[0 1] [2 3]]]
  {:a a :b b :c c :d d}) ; => {:a 0 :b 1 :c 2 :d 3}

(let [[[a b] [c d]] [[0 1] [2 3]]]
  {:a a :b b :c c :d d}) ; => {:a 0 :b 1 :c 2 :d 3}


;; 3. destructure and keep the map
(let [... {:a "foo" :b "bar"}]
  [a b m]) ; => "foo" {:a "foo" :b "bar"}

(let [{:keys [a b] :as m} {:a "foo" :b "bar"}]
  [a b m]) ; => ["foo" "bar" {:a "foo" :b "bar"}]


;; 4. destructure with variable arguments
(defn main [...]
  (apply str args))

(main "foo" "bar" "blitz") ; => "foobarblitz"

(defn main [& args]
  (apply str args))

(main "foo" "bar" "blitz") ; => "foobarblitz"


;; 5. destructure nested string value
(let [... {:foo {"bar" "quux"}}]
  bar) ; => "quux"

(let [{{:strs [bar]} :foo} {:foo {"bar" "quux"}}]
  bar) ; => "quux"


;; 6. destructure a qualified keyword
(let [{:keys [...]} {:user/name "Rich"}]
  ...) ; => "Rich"

(let [{:keys [user/name]} {:user/name "Rich"}]
  name) ; => "Rich"


;; 7. Destructure with default parameters
(defn greet [...]
  (str "Hi " name))

(greet {:id 1 :name "Sara"}) ; => "Hi Sara"
(greet {:id 2}) ; => "Hi Nameless"

(defn greet [{:keys [name] :or {name "Nameless"}}]
  (str "Hi " name))

(greet {:id 1 :name "Sara"}) ; => "Hi Sara"
(greet {:id 2}) ; => "Hi Nameless"


;; 8. destructure with variable arguments and defauls values
(defn main [...]
  (str mode " " port))

(main :mode :production :port 8080) ;; => ":production 8080"
(main :foo :bar) ;; => ":test 3000"

;; destructure with variable arguments and defauls values
(defn main [& {:keys [mode port]
               :or {port 3000 mode :test} :as args}]
  (str mode " " port))

(main :mode :production :port 8080) ;; => ":production 8080"
(main :foo :bar) ;; => ":test 3000"


;; 9. ignore parts, pick out a specific position and gather the rest
(let [... ["Mr" "Willy" "Junior" "Wonka" "Factory"]]
  (str "Hi " name " (" (clojure.string/join "-" rest) ")"))
;; => "Hi Willy (Junior-Wonka-Factory)"

;; ignore parts, pick out a specific position and gather the rest
(let [[_ name & rest] ["Mr" "Willy" "Junior" "Wonka" "Factory"]]
  (str "Hi " name " (" (clojure.string/join "-" rest) ")"))
;; => "Hi Willy (Junior-Wonka-Factory)"


;; 10. A bit of everything
(let [... {:lists [1 5 7 9]}]
  (str "first " first " rest " rem)) ; => first 1 rest (5 7 9)

(let [{[first & rem] :lists} {:lists [1 5 7 9]}]
  (str "first " first " rest " rem)) ; => first 1 rest (5 7 9)
