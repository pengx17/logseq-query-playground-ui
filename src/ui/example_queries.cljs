(ns ui.example-queries)

(def examples
  {:block-with-markers-and-children '[:find [(pull ?b [:db/id
                                                       :block/uuid
                                                       :block/parent
                                                       :block/left
                                                       :block/refs
                                                       :block/content
                                                       :block/marker
                                                       {:block/_parent ...}]) ...]
                                      :where
                                      [?b :block/uuid]
                                      [?b :block/marker]]

   :query-by-id '[:find (pull ?p [*])
                  :where
                  [24 :block/uuid ?uid]
                  [?p :block/uuid ?uid]]})