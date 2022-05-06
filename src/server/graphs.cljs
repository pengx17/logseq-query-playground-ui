(ns server.graphs
  (:require ["fs$promises" :as fsp]
            [promesa.core :as p]
            ["path" :as path]
            [datascript.transit :as dt]
            [datascript.core :as d]
            [clojure.edn :as edn]
            ["os" :as os]))

(defn expand-home
  [path]
  (path/join (os/homedir) path))

(defn entries
  [dir]
  (fsp/readdir dir))

(defn slurp
  [file]
  (fsp/readFile file "utf8"))

(defn file-exists?
  [file]
  (fsp/exists file))

(defn directory?
  [file]
  (p/let [stat (fsp/lstat file)]
    (.isDirectory stat)))

;; graph utils
(defn get-graph-paths
  []
  (let [dir (expand-home ".logseq/graphs")]
    (p/->> (entries dir)
           (filter #(re-find #".transit$" %))
           (map #(str dir "/" %)))))

(defn full-path->graph
  [path]
  (second (re-find #"\+\+([^\+]+).transit$" path)))

(defn get-graph-path
  [graph]
  (p/let [graphs (get-graph-paths)]
    (some #(when (= graph (full-path->graph %)) %)
          graphs)))

(def graph-paths
  (p/->> (get-graph-paths)
         (mapv (juxt full-path->graph identity))))

(defn run-query
  [graph-name query-string]
  (p/let [query (edn/read-string query-string)
          db-path (get-graph-path graph-name)
          db-str (slurp db-path)
          db (dt/read-transit-str db-str)
          res (->> db
                   (d/q query)
                   vec)]
    res))
