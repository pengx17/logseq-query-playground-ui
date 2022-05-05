(ns server.core
  (:require [macchiato.middleware.params :as params]
            [macchiato.middleware.restful-format :as rf]
            [macchiato.server :as http]
            [macchiato.util.response :refer [content-type header]]
            [promesa.core :as p]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [server.graphs :as graphs]))

(defn wrap-body-to-params
  [handler]
  (fn [request respond raise]
    (handler (-> request
                 (assoc-in [:params :body-params] (:body request))
                 (assoc :body-params (:body request))) respond raise)))

;; Wrap all response's content type with json type
(defn wrap-content-type-json
  [handler]
  (fn [request respond _]
    (handler request #(respond (content-type % "application/json")))))

(defn wrap-cors-header
  [handler]
  (fn [request respond _]
    (handler request #(respond (header % "Access-Control-Allow-Origin" "*")))))

(defn wrap-coercion-exception
  "Catches potential synchronous coercion exception in middleware chain"
  [handler]
  (fn [request respond _]
    (try
      (handler request respond _)
      (catch :default e
        (let [exception-type (:type (.-data e))]
          (cond
            (= exception-type :reitit.coercion/request-coercion)
            (respond {:status 400
                      :body   {:message "Bad Request"}})

            (= exception-type :reitit.coercion/response-coercion)
            (respond {:status 500
                      :body   {:message "Bad Response"}})
            :else
            (respond {:status 500
                      :body   {:message "Truly internal server error"}})))))))

(defn get-graphs-handler [_ respond]
  (p/let [graphs graphs/graph-paths]
    (respond {:status 200
              :body   {:graphs (map first graphs)}})))

(defn get-graph-handler [request respond]
  (p/let [graphs graphs/graph-paths
          graph-name (get-in request [:path-params :name])
          graph (some (fn [g]
                        (when (= (first g) graph-name) g)) graphs)]
    (if graph
      (p/let [data (graphs/slurp (second graph))]
        (respond {:status 200
                  :body  {:name graph-name
                          :data data}}))
      (respond {:status 404
                :body   {:message "Not found"}}))))

(defn run-graph-query-handler [request respond]
  (p/catch
   (p/let [graph-name (get-in request [:path-params :name])
           query (get-in request [:query-params "query"])
           data (graphs/run-query graph-name query)]
     (respond {:status 200
               :body  {:name graph-name
                       :data (pr-str data)}}))
   (fn [e]
     (respond {:status 500
               :body   {:message (.-message e)}}))))

(def routes
  [["/graphs"       {:get get-graphs-handler}]
   ["/graphs/:name" {:get run-graph-query-handler}]])

(def app
  (ring/ring-handler
   (ring/router
    [routes]
    {:data {:middleware [params/wrap-params
                         wrap-cors-header
                         #(rf/wrap-restful-format % {:keywordize? false})
                         wrap-body-to-params
                         wrap-coercion-exception
                         wrap-content-type-json
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})
   (ring/create-default-handler)))

(def *server-instance (atom nil))

(defn server []
  (println "starting server ...")
  (let [host "127.0.0.1"
        port 3301]
    (reset! *server-instance
            (http/start
             {:handler    app
              :host       host
              :port       port
              :on-success #(println "macchiato server started on" host ":" port)}))))

(defn ^:dev/before-load stop []
  (swap! *server-instance
         (fn [v] (when-not (nil? v)
                   (.close @*server-instance)
                   (js/console.log "stopping existing server ...")))))
