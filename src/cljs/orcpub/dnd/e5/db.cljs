(ns orcpub.dnd.e5.db
  (:require [orcpub.route-map :as route-map]
            [orcpub.user-agent :as user-agent]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi5e]
            [re-frame.core :as re-frame]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [cljs.spec.alpha :as spec]
            [cljs.reader :as reader]
            [bidi.bidi :as bidi]
            [cljs-http.client :as http]
            [cljs.pprint :refer [pprint]]))

(def local-storage-character-key "character")
(def local-storage-user-key "user")
(def local-storage-magic-item-key "magic-item")

(def default-route route-map/dnd-e5-char-builder-route)

(defn parse-route []
  (let [route (if js/window.location
                (bidi/match-route route-map/routes js/window.location.pathname))]
    (if route
      route
      default-route)))

(def default-character (char5e/set-class t5e/character :barbarian 0 t5e/barbarian-option))

(def default-value
  {:builder {:character {:tab #{:build :options}}}
   :character default-character
   :template t5e/template
   :plugins t5e/plugins
   :locked-components #{}
   :route (parse-route)
   :route-history (list default-route)
   :return-route default-route
   :registration-form {:send-updates? true}
   :device-type (user-agent/device-type)})

(defn set-item [key value]
  (try
    (.setItem js/window.localStorage key value)
    (catch js/Object e (prn "FAILED SETTING LOCALSTORAGE ITEM"))))

(defn character->local-store [character]
  (if js/window.localStorage
    (set-item local-storage-character-key
              (str (assoc (char5e/to-strict character)
                          :changed
                          (:changed character))))))

(defn user->local-store [user-data]
  (if js/window.localStorage
    (set-item local-storage-user-key (str user-data))))

(defn magic-item->local-store [magic-item]
  (if js/window.localStorage
    (set-item local-storage-magic-item-key (str magic-item))))

(def tab-path [:builder :character :tab])

(defn get-local-storage-item [local-storage-key]
  (if-let [stored-str (if js/window.localStorage
                        (.getItem js/window.localStorage local-storage-key))]
    (try (reader/read-string stored-str)
         (catch js/Object e (js/console.warn "UNREADABLE ITEM FOUND" local-storage-key stored-str)))))

(defn reg-local-store-cofx [key local-storage-key item-spec & [item-fn]]
  (re-frame/reg-cofx
   key
   (fn [cofx _]
     (assoc cofx
            key
            (if-let [stored-item (get-local-storage-item local-storage-key)]
              (if (spec/valid? item-spec stored-item)
                (if item-fn
                  (item-fn stored-item)
                  stored-item)
                (do
                  (js/console.warn "INVALID ITEM FOUND, IGNORING")
                  (pprint (spec/explain-data item-spec stored-item)))))))))

(reg-local-store-cofx
 :local-store-character
 local-storage-character-key
 ::se/entity
 (fn [char]
   (assoc
    (char5e/from-strict char)
    :changed
    (:changed char))))

(spec/def ::username string?)
(spec/def ::email string?)
(spec/def ::token string?)
(spec/def ::theme string?)
(spec/def ::user-data (spec/keys :req-un [::username ::email]))
(spec/def ::user (spec/keys :opt-un [::user-data ::token ::theme]))

(reg-local-store-cofx
 :local-store-user
 local-storage-user-key
 ::user)

(reg-local-store-cofx
 :local-store-magic-item
 local-storage-magic-item-key
 ::mi5e/internal-magic-item)
