(ns playground.index
  (:require [rum.core :as rum]))

(defonce app-state (atom {:text "Hello world!"}))

(rum/defc hello-world [data]
  [:div
   [:h1 (:text @app-state)]
   [:h3 "Edit this and watch it change!" " " (:foo data)]])

(defn ^:dev/after-load  start []
  ;; start is called by init and after code reloading finishes
  ;; this is controlled by the :after-load in the config
  (rum/mount (hello-world {:foo "bar"})
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
