(ns ui.core
  (:require ["shiki" :as shiki]
            ["swr$default" :as use-swr]
            [cljs-bean.core :as bean]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [promesa.core :as p]
            [rum.core :as rum]
            [ui.code :as code]
            [ui.example-queries :as examples]))

(shiki/setCDN "shiki/")

(def highlighter$ (shiki/getHighlighter #js{:theme "github-light"
                                            :langs ["clojure" "json"]}))
(def *hightlighter (atom nil))

(p/then highlighter$ #(reset! *hightlighter %))

(defn use-highlighter []
  (let [[hl set-hl] (rum/use-state @*hightlighter)]
    (rum/use-layout-effect!
     (fn [] (p/then highlighter$
                    (fn [_hl] (set-hl
                               (fn [hl] (when (nil? hl) (set-hl _hl)))))) #()) []) hl))

(defn use-highlight-fn []
  (if-let [hl (use-highlighter)]
    #(.codeToHtml hl % #js{:lang "json"})
    identity))

(defn edn->str [obj] (with-out-str (pprint/pprint obj)))

;; persist to session storage
(defonce session-storage-key "code.editor")

(defn persist! [value]
  (.setItem js/sessionStorage session-storage-key (edn->str value)))

(defn restore []
  (let [value (.getItem js/sessionStorage session-storage-key)]
    (edn/read-string value)))

;; fetcher for swr
(defn fetcher
  ([path]
   (fetcher path #js{:method "get"}))
  ([path opts]
   (p/let [resp (js/fetch (str "http://localhost:3301" path) opts)
           resp-json (.json resp)]
     resp-json)))

(def graphs-fetcher fetcher)
(defn graphs-query-fetcher
  [[graph-name query]]
  (fetcher (str "/graphs/" graph-name "?query=" (js/encodeURIComponent query))))

(defn use-graphs []
  (let [resp (use-swr "/graphs" graphs-fetcher)
        data (.-data resp)
        loading (nil? data)
        graphs (when-not loading (.-graphs data))]
    graphs))

(defn use-graph-query [graph-name query-string]
  (let [param (rum/use-memo
               #(and graph-name query-string [graph-name query-string])
               [graph-name query-string])
        resp (use-swr param graphs-query-fetcher)
        data (.-data resp)
        loading (nil? data)
        error (when (not loading) (.-message data))
        data (when-not (or loading error) (-> data .-data edn/read-string))]
    [data error]))

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

(rum/defc graph-query [graph-name]
  [:div
   (let [[query-string set-query]
         (rum/use-state (or (restore)
                            (edn->str (:block-with-markers-and-children examples/examples))))

         [data error] (use-graph-query graph-name query-string)
         highlight (use-highlight-fn)]
     (rum/use-effect! (fn [] (persist! query-string) #()) [query-string])
     (if graph-name
       [:div.relative
        [:textarea.border-2.border-gray-500.w-full.p-2.top-0.sticky.h-48.font-mono.hidden
         {:value query-string
          :on-change (fn [e] (set-query (.. e -target -value)))}]
        (code/editor query-string set-query)
        (if error
          [:span.text-red-500 error]
          [:<>
           [:div (if data (str "Count: " (count data)) "loading ...")]
           (map-indexed
            (fn [idx row]
              (let [raw-html (highlight (js/JSON.stringify (bean/->js row) nil 2))]
                [:pre.whitespace-pre-wrap.py-2.text-xs.hover:bg-gray-100
                 {:key idx
                  :dangerouslySetInnerHTML
                  {:__html raw-html}}]))
            data)])]
       [:span "no graph selected"]))])

(rum/defc main []
  [:div.p-4
   (let [[graph set-graph] (rum/use-state nil)]
     [:div
      (graphs-select graph set-graph)
      (graph-query graph)])])

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
