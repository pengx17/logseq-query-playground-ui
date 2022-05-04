(ns playground.index
  (:require [rum.core :as rum]
            ["browser-fs-access" :as fs]
            [datascript.transit :as dt]
            [datascript.core :as d]
            [promesa.core :as p]))

(def path-persisted-key "playground:path")

(defn store-to-local-storage! [key data]
  (.setItem js/localStorage key (js/JSON.stringify (clj->js data))))

(defn load-from-local-storage
  ([key]
   (load-from-local-storage key nil))
  ([key default]
   (try
     (or (js->clj (js/JSON.parse (.getItem js/localStorage key)))
         default)
     (catch js/Object _
       default))))

(def *graph-db-glob-path (atom (load-from-local-storage path-persisted-key "")))

(rum/defc graph-db-path-control
  [value on-change]
  (let [[local-value set-local-value!] (rum/use-state value)]
    [:div [:label.inline
           [:<>
            [:div.font-light.text-xl "Graph DB Path"]
            [:div.flex
             [:input.border-2.border-r-0.border-gray-500.p-2.w-full
              {:placeholder "Enter graph db path"
               :required true
               :on-change (fn [e] (set-local-value! (.. e -target -value)))
                     ;; validate if this is a valid path
                     ;; :pattern #".transit$"
               :value local-value}]
             [:button.border-2.border-gray-500.px-4.hover:bg-gray-100.active:bg-gray-200
              {:on-click (fn []
                           (fs/fileOpen (clj->js {}))
                           (on-change local-value))} "Save"]]]]]))

(defn set-graph-db-path! [path]
  (store-to-local-storage! path-persisted-key path)
  (reset! *graph-db-glob-path path))

(rum/defc main < rum/reactive []
  [:div.p-4
   (graph-db-path-control (rum/react *graph-db-glob-path)
                          set-graph-db-path!)])

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
