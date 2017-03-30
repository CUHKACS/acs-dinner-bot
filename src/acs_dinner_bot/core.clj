(ns acs-dinner-bot.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clj-http.client :as client]
            [alandipert.enduro :refer [file-atom]]
            [clj-time.core :refer [day-of-week hour minute]]
            [clj-time.local :refer [local-now]]
            [instaparse.core :as insta]
            [io.pedestal.log :as log]
            [io.pedestal.http :as http]
            [io.pedestal.http.body-params :refer [body-params]]
            [io.pedestal.http.route.definition :refer [defroutes]]))

(def config
  (edn/read-string
    (slurp (io/resource "config.edn"))))

(def location-db (file-atom (:default-db config) "location-db.edn"))

(def parse-message
  (insta/parser (io/resource "message.bnf")))

(defmulti time-match?
  "(time-match? filter current-time)"
  (fn [[filter-type & _] _] filter-type))

(defmethod time-match? :opening-hours
  [[_ open-at close-at] datetime]
  (<= open-at
      (+ (* 100 (hour datetime)) (minute datetime))
      close-at))

(defmethod time-match? :closes-on
  [[_ weekday] datetime]
  (not= (day-of-week datetime) weekday))


(defmulti match-tags?
  "(match-tags? filter tags)"
  (fn [[filter-type _] _] filter-type))

(defmethod match-tags? :ContainsTag
  [[_ [_ tag]] tags]
  (contains? tags tag))

(defmethod match-tags? :ExcludeTag
  [[_ [_ tag]] tags]
  (not (contains? tags tag)))

;; Facebook API
;;--------------

(defn reply-message [{:keys [sender text]}]
  (let [now         (local-now)
        message-ast (parse-message text)
        reply       (if (insta/failure? message-ast)
                      "???"
                      (->> @location-db
                           ;; filter by time constraint
                           (filter (fn [{:keys [time-filters]}]
                                     (every? #(time-match? % now)
                                             time-filters)))
                           ;; filter by tags
                           (filter (fn [{:keys [tags]}]
                                     (every? #(match-tags? (second %) tags)
                                             (rest message-ast))))
                           ;; return :\ emoji if no match
                           (#(if (empty? %) [{:name ":\\"}] %))
                           (rand-nth)
                           (:name)))]
    (client/post
      "https://graph.facebook.com/v2.6/me/messages"
      {:query-params {:access_token (:access-token config)}
       :form-params  {:recipient  {:id    sender}
                      :message    {:text  reply}}
       :content-type :json})))

(defn webhook-validate [{{mode      :hub.mode
                          token     :hub.verify_token
                          challenge :hub.challenge} :query-params}]
  (if (and (= mode  "subscribe")
           (= token (:validate-token config)))
    {:status 200 :body challenge}
    {:status 403}))

(defn webhook-message [{{:keys [object entry]} :json-params}]
  (when (= object "page")
    (future
      (doseq [message (->> entry
                           (map :messaging)
                           (flatten)
                           (map (fn [messaging]
                                  {:sender (get-in messaging [:sender :id])
                                   :text   (get-in messaging [:message :text])}))
                           (filter :text))]
        (log/info :recieved-message message)
        (reply-message message))))
  {:status 200})

;; DB API
;;-------------

(defn location-get [_]
  {:status 200 :body @location-db})

(defn location-save [req]
  (log/info :save-req req))

;; Server Config
;;---------------

(defroutes routes
  [[["/" ^:interceptors [(body-params)]
     ["/webhook"  {:get   webhook-validate
                   :post  webhook-message}]
     ["/location" {:get   location-get
                   :post  location-save}]]]])

(defn -main [& args]
  (-> {::http/routes        routes
       ::http/resource-path "/public"
       ::http/type          :jetty
       ::http/port          8000}
      http/create-server
      http/start))

