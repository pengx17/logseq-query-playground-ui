(ns server.graphs
  (:require ["fs$promises" :as fsp]
            [promesa.core :as p]
            ["path" :as path]
            ["os" :as os]))

(defn expand-home
  [path]
  (path/join (os/homedir) path))

(defn entries
  [dir]
  (fsp/readdir dir))

(defn slurp
  [file]
  (fsp/readFile file))

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
