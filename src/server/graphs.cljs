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

(def rules
  ;; rule "parent" is optimized for child node -> parent node nesting queries
  '[[(parent ?p ?c)
     [?c :block/parent ?p]]
    [(parent ?p ?c)
     [?c :block/parent ?t]
     (parent ?p ?t)]

  ;; rule "child" is optimized for child node -> parent node nesting queries
    [(child ?p ?c)
     [?c :block/parent ?p]]
    [(child ?p ?c)
     [?t :block/parent ?p]
     (child ?t ?c)]

  ;; rule "namespace" is optimized for child node -> node of upper namespace level nesting queries
    [(namespace ?p ?c)
     [?c :block/namespace ?p]]
    [(namespace ?p ?c)
     [?t :block/namespace ?p]
     (namespace ?t ?c)]

    ;; Select rules carefully, as it is critical for performance.
    ;; The rules have different clause order and resolving directions.
    ;; Clause order Reference:
    ;; https://docs.datomic.com/on-prem/query/query-executing.html#clause-order
    ;; Recursive optimization Reference:
    ;; https://stackoverflow.com/questions/42457136/recursive-datalog-queries-for-datomic-really-slow
    ;; Should optimize for query the decendents of a block
    ;; Quote:
    ;; My theory is that your rules are not written in a way that Datalog can optimize for this read pattern - probably resulting in a traversal of all the entities. I suggest to rewrite them as follows:
    ;; [[(ubersymbol ?c ?p)
    ;;   (?c :ml/parent ?p)]
    ;;  [(ubersymbol ?c ?p)
    ;;   ;; we bind a child of the ancestor, instead of a parent of the descendant
    ;;   (?c1 :ml/parent ?p)
    ;;   (ubersymbol ?c ?c1)]]

    ;; This way of writing the ruleset is optimized to find the descendants of some node. The way you originally wrote it is optimized to find the anscestors of some node.

    ;; from https://stackoverflow.com/questions/43784258/find-entities-whose-ref-to-many-attribute-contains-all-elements-of-input
    ;; Quote:
    ;; You're tackling the general problem of 'dynamic conjunction' in Datomic's Datalog.
    ;; Write a dynamic Datalog query which uses 2 negations and 1 disjunction or a recursive rule
    ;; Datalog has no direct way of expressing dynamic conjunction (logical AND / 'for all ...' / set intersection).
    ;; However, you can achieve it in pure Datalog by combining one disjunction
    ;; (logical OR / 'exists ...' / set union) and two negations, i.e
    ;; (For all ?g in ?Gs p(?e,?g)) <=> NOT(Exists ?g in ?Gs, such that NOT(p(?e, ?g)))

    ;; [(matches-all ?e ?a ?vs)
    ;;  [(first ?vs) ?v0]
    ;;  [?e ?a ?v0]
    ;;  (not-join [?e ?vs]
    ;;            [(identity ?vs) [?v ...]]
    ;;            (not-join [?e ?v]
    ;;                      [?e ?a ?v]))]
    ])


(defn run-query
  [graph-name query-string]
  (p/let [query (edn/read-string query-string)
          db-path (get-graph-path graph-name)
          db-str (slurp db-path)
          db (dt/read-transit-str db-str)
          res (as-> db v
                (d/q query v rules)
                (vec v))]
    res))
