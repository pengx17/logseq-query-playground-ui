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
                                      :in $ %
                                      :where
                                      [?b :block/uuid]
                                      [?b :block/marker]]

   :query-by-id '[:find (pull ?p [*])
                  :in $ %
                  :where
                  [24 :block/uuid ?uid]
                  [?p :block/uuid ?uid]]

   :query-id-children '[:find [(pull ?c [*]) ...]
                        :in $ %
                        :where
                        (child ?p ?c)
                        [68 :block/uuid ?uid]
                        [?p :block/uuid ?uid]
                        [?c :block/uuid]]

   :query-by-id-and-children '[:find (pull ?p [* {:block/_parent ...}])
                               :in $ %
                               :where
                               [68 :block/uuid ?uid]
                               [?p :block/uuid ?uid]]})
