(ns ui.core
  (:require [rum.core :as rum]
            ["swr$default" :as use-swr]
            [datascript.transit :as dt]
            [datascript.core :as d]
            [clojure.pprint :as pprint]
            [clojure.edn :as edn]
            [promesa.core :as p]))

;; fetcher for swr
(defn fetcher
  [path]
  (p/let [resp (js/fetch (str "http://localhost:3301" path))
          resp-json (.json resp)]
    resp-json))

(defn use-graphs []
  (let [resp (use-swr "/graphs" fetcher)
        data (.-data resp)
        loading (nil? (.-data resp))
        graphs (when-not loading (.-graphs data))]
    graphs))

(defn use-graph-db [graph-name]
  (let [resp (use-swr (and graph-name (str "/graphs/" graph-name)) fetcher)
        data (.-data resp)
        loading (nil? (.-data resp))
        db-str (when-not loading (.-data data))
        [db set-db] (rum/use-state nil)]
    (rum/use-effect!
     (fn []
       (when-not (empty? db-str)
         (set-db (dt/read-transit-str db-str)))
       #()) [db-str])
    db))

(rum/defc graphs-select
  "A control that shows all locally install graphs"
  [value on-change]
  (let [graphs (use-graphs)]
    (rum/use-effect!
     (fn []
       (when (and (nil? value) graphs)
         (on-change (first graphs)))
       #()) [value graphs])
    (if-not (seq graphs)
      [:span "loading ..."]
      [:div.inline-flex.border.gap-1
       (map
        (fn [graph]
          (let [selected (= value graph)]
            [:label.p-2 {:key graph :class (when selected "bg-gray-200")}
             [:input.mr-2 {:type "radio"
                           :name "graph-select"
                           :on-change (fn [] (on-change graph))
                           :checked selected}]
             [:span graph]]))
        graphs)])))

(def query-edn '[:find (pull ?b [*])
                 :where
                 [?b :block/marker]
                 [?b :block/page ?p]
                 [?p :block/journal? true]])

(defn edn->str [obj] (with-out-str (pprint/pprint obj)))

(rum/defc graph-query [db]
  [:div
   (let [[query-string set-query] (rum/use-state (edn->str query-edn))
         [res set-res] (rum/use-state nil)]
     (rum/use-effect!
      (fn []
        (when (and db query-string)
          (try
            (let [query (edn/read-string query-string)
                  res (->> db
                           (d/q query)
                           flatten
                           vec)]
              (set-res res))
            (catch :default e (println e))))
        #())
       [query-string db])
     (if db
       [:div.relative
        [:textarea.border-2.border-gray-500.w-full.p-2.top-0.sticky.h-48.font-mono
         {:value query-string
          :on-change (fn [e] (set-query (.. e -target -value)))}]
        [:div "Count: " (count res)]
        [:pre.whitespace-pre-wrap (edn->str res)]]
       [:span "no graph selected"]))])

(rum/defc main []
  [:div.p-4
   (let [[graph set-graph] (rum/use-state nil)
         db (use-graph-db graph)]
     [:div
      (graphs-select graph set-graph)
      (graph-query db)])])

(defn ^:dev/after-load  start []
  ;; start is called by init and after code reloading finishes
  ;; this is controlled by the :after-load in the config
  (rum/mount (main)
             (. js/document (getElementById "app"))))

(defn ^:export init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (start))

(defn stop []
  ;; stop is called before any code is reloaded
  ;; this is controlled by :before-load in the config
  (js/console.log "stop"))
