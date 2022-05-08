(ns ui.code
  (:require ["@codemirror/fold" :as fold]
            ["@codemirror/gutter" :refer [lineNumbers]]
            ["@codemirror/highlight" :as highlight]
            ["@codemirror/history" :refer [history historyKeymap]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/view" :as view :refer [EditorView]]
            [applied-science.js-interop :as j]
            [nextjournal.clojure-mode :as cm-clj]
            [nextjournal.clojure-mode.live-grammar :as live-grammar]
            [rum.core :as rum]))

(defn- make-state [extensions doc]
  (.create EditorState
           #js{:doc doc
               :extensions (cond-> #js[(.. EditorState -allowMultipleSelections (of true))]
                             extensions (j/push! extensions))}))

(def theme
  (.theme EditorView
          (j/lit {".cm-content" {:white-space "pre-wrap"
                                 :padding "10px 0"}
                  "&.cm-focused" {:outline "none"}
                  ".cm-line" {:padding "0 9px"
                              :line-height "1.6"
                              :font-size "16px"
                              :font-family "var(--code-font)"}
                  ".cm-matchingBracket" {:border-bottom "1px solid var(--teal-color)"
                                         :color "inherit"}
                  ".cm-gutters" {:background "transparent"
                                 :border "none"}
                  ".cm-gutterElement" {:margin-left "5px"}
                  ;; only show cursor when focused
                  ".cm-cursor" {:visibility "hidden"}
                  "&.cm-focused .cm-cursor" {:visibility "visible"}})))

(defonce extensions
  #js[theme
      (history)
      highlight/defaultHighlightStyle
      (view/drawSelection)
      (lineNumbers)
      (fold/foldGutter)
      (.. EditorState -allowMultipleSelections (of true))
      (if false
        ;; use live-reloading grammar
        #js[(cm-clj/syntax live-grammar/parser)
            (.slice cm-clj/default-extensions 1)]
        cm-clj/default-extensions)
      (.of view/keymap cm-clj/complete-keymap)
      (.of view/keymap historyKeymap)])

;; https://codemirror.net/6/docs/guide/#functional-core%2C-imperative-shell
(rum/defc editor
  [source on-change]
  (let [*anchor (rum/use-ref nil)
        *cm-view (rum/use-ref nil)
        on-change-extension (.. EditorView -updateListener
                                (of (fn [view-update]
                                      (on-change
                                       (.. view-update -state -doc (toString))))))
        extensions (j/push! #js[extensions] on-change-extension)]
    (rum/use-effect!
     (fn []
       (if-not (rum/deref *cm-view)
         (rum/set-ref! *cm-view
                       (EditorView.
                        (j/obj :state (make-state extensions source)
                               :parent (rum/deref *anchor))))
         (let [cm-view (rum/deref *cm-view)]
           (when
            (not= source (.. cm-view -state -doc (toString)))
             (.dispatch cm-view (j/lit {"changes" {"from" 0
                                                   "to" (.. cm-view -state -doc -length)
                                                   "insert" source}})))))
       #())
     [source])

    (rum/use-effect!
     (fn [] #())
     [])
    [:div.code-mirror-anchor.border-2.border-gray-500.w-full.p-2.top-0.bg-white.max-h-96.overflow-auto.sticky
     {:ref *anchor}]))
