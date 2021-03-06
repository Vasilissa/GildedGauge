(ns gilded-gauge.scrape
  (:require [net.cgrand.enlive-html :refer [html-resource select]]
            [clojure.core.async :refer [go <!!]]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]))


(def output-path  "src/gilded_gauge/rankings.cljs")
(def ranking-url  "https://www.forbes.com/ajax/list/data?year=2018&uri=billionaires&type=person")
(def wiki-url     "https://en.wikipedia.org/wiki/")
(def thumb-prefix "//upload.wikimedia.org/wikipedia/commons/thumb/")

(def prefix-n (count thumb-prefix))
(def take-n   50)

(defn pred [m]
  (when-let [p (:position m)]
    (<= p take-n)))


(defn scale [n]
  (/ n 1000))


(defn get-image [m]
  (go
    (try
      (assoc
        m
        :img
        (some->
          (str wiki-url (str/replace (:name m) #"\s" "_"))
          java.net.URL.
          html-resource
          (select [:.biography :img])
          first
          :attrs
          :src
          (->>
            (drop prefix-n)
            (apply str))))
      (catch Exception e
        m))))


(defn drop-nil-img [v]
  (let [img (nth v 2)]
    (if (or (nil? img) (.contains img ".svg"))
      (pop v)
      v)))


(with-open [w (-> output-path io/file io/writer)]
  (binding [*out* w]
    (let [rankings
          (->>
            (json/read-str (slurp ranking-url) :key-fn keyword)
            (filter pred)
            (sort-by :position)
            (map #(select-keys % [:name :worth]))
            (map #(update % :worth scale))
            (map get-image)
            doall
            (map <!!)
            (map (juxt :name :worth :img))
            (map drop-nil-img)
            vec)]
      (pprint `(~'ns gilded-gauge.rankings "Autogenerated namespace"))
      (pprint `(def ~'rankings ~rankings)))))
