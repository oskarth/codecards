(ns fingertips.util
  (:require [clj-time.core :as time]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc])
  (:import java.util.Base64))

(defn dbg [s x]
  (println (str "DBG" s))
  (clojure.pprint/pprint x)
  x)

(defn gen-uuid [] (str (java.util.UUID/randomUUID)))

(defn now [] (str (time/now)))

(defn past? [dt-str]
  (time/before? (tf/parse (tf/formatter :date-time) dt-str)
                (time/now)))

(defn date-time? [dt-str]
  (try (tf/parse (tf/formatter :date-time) dt-str)
       (catch Exception e)))

(defn within-a-week? [dt-str]
  (time/before? (tf/parse (tf/formatter :date-time) dt-str)
                (time/plus (time/now) (time/days 7))))

(defn otp []
  (apply str (map (fn [_] (rand-int 10)) (range 6))))

(defn encode-base64 [s]
  (.encodeToString (Base64/getEncoder) (.getBytes s)))

;; Returns nil if something is wrong with the base64.
(defn decode-base64 [s]
  (try (String. (.decode (Base64/getDecoder) s))
       (catch Exception e)))

(defn qualify-map [ns-str m]
  (->> m
       (map (fn [[k v]] [(keyword ns-str (name k)) v]))
       (into {})))
