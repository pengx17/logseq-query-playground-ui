(ns server.core
  (:require [macchiato.server :as http]
            [reitit.ring :as ring]
            [macchiato.middleware.params :as params]
            [reitit.ring.coercion :as rrc]
            [macchiato.middleware.restful-format :as rf]))

(defn wrap-body-to-params
  [handler]
  (fn [request respond raise]
    (handler (-> request
                 (assoc-in [:params :body-params] (:body request))
                 (assoc :body-params (:body request))) respond raise)))

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

(def routes
  [["/test"
    {:get  {:parameters {:query {:name string?}}
            :responses  {200 {:body {:message string?}}}
            :handler    (fn [request respond _]
                          (respond {:status 200 :body {:message (str "Hello: " (-> request :parameters :query :name))}}))}
     :post {:parameters {:body {:my-body string?}}
            :handler    (fn [request respond _]
                          (respond {:status 200 :body {:message (str "Hello: " (-> request :parameters :body :my-body))}}))}}]])

(def app
  (ring/ring-handler
   (ring/router
    [routes]
    {:data {:middleware [params/wrap-params
                         #(rf/wrap-restful-format % {:keywordize? true})
                         wrap-body-to-params
                         wrap-coercion-exception
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]}})
   (ring/create-default-handler)))


(defn server []
  (println "Hey I am running now!")
  (let [host "127.0.0.1"
        port 3301]
    (http/start
     {:handler    app
      :host       host
      :port       port
      :on-success #(println "macchiato-test started on" host ":" port)})))