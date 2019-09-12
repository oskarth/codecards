# Deploying

Uberjar:
```
boot build
java -jar target/fingertips-api.jar

```

Where will events.log be written?

`./bin/deploy`

On server:
```
# kill server
boot repl

# In REPL
(require 'fingertips.server)'
```


## Google Analytics

Tracking:

- Navigation: Home / Add / Review / Login / Payment (wants to buy) / Purchase (has entered something into CC form)
- Command: review-card / new-card / register / auth


## Env / secrets

Secrets are in `secrets/{production,staging,env}`. So there's a file `APP_ENV` with contents `staging`, say. This is then in the environment as `APP_ENV=staging` by using `envdir`.

## Design etc

Consider something like
http://purecss.io/buttons/


Easy:
envdir ~/fingertips/env boot repl -s -p 9000 wait

To change payment, search for `amount` in app.js and server.clj, and `one-time` in index.


## Misc notes


;; Unlocked edit card, even though not done. I think.
;; Key is adding collections
;; A collection is a set of cards that you fork
;; Forking a card is keeping the Q and A but with a new ID

;; Should maybe have stayed and napped, dunno
;; Focused on the thing - how do we create a new collection?

;; Can we keep it really simple with ids?
;; user id, card id, review id, collection id
;; then you can fork reviews and collections
;; and a user has a set of tokens, etc
;; maybe

;; small, composable parts

;; collection - id, name, set of cards
;; can we simulate this first somehow?
;; like play with commands without all this

(pprint (take 2 (:cards @db)))

;; Use as->:



;; we really want to do compactification for all thee things, don't we?
;; ->> foo ... ...)
;; Can only add cards you own to collection, at least without forking


## Ideas collection

Clojure threading


## TODO

Increment version in js to cache bust.
