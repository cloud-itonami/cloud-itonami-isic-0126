(ns oleaginousops.store
  "Store abstraction for oleaginous-fruit plantation/block/planting
  records. Current implementation is an in-memory map; production should
  migrate to Datomic/kotoba-server (the same seam point all cloud-itonami
  actors use). Mirrors `berrynutops.store` (cloud-itonami-isic-0125) in
  shape.

  A registered plantation block is the minimal unit of authority: a
  plantation/block must be registered before ANY proposal referencing it
  can be considered by the Governor (see `oleaginousops.governor`'s
  `plantation-registered` invariant). Plantation data is opaque to this
  namespace -- callers/backends decide what a plantation record contains
  (name, location, oil-crop species, planted area, etc); this Store only
  answers \"is this plantation-id registered, and if so what's on file\".")

;; Protocol for swappable store implementations
(defprotocol Store
  (registered-plantation [store plantation-id]
    "Retrieve a registered plantation block record by ID. Returns nil if
    the plantation-id is nil or not registered."))

;; In-memory implementation (MemStore) for development/testing
(defrecord MemStore [plantations]
  Store
  (registered-plantation [_store plantation-id]
    (when plantation-id
      (get @plantations plantation-id))))

(defn mem-store
  "Create an in-memory store. `initial-plantations` is an optional map of
  plantation-id -> plantation-record."
  [& [{:keys [initial-plantations] :or {initial-plantations {}}}]]
  (MemStore. (atom initial-plantations)))

(defn add-plantation
  "Register or update a plantation block in the store. Used by tests and
  simulation."
  [^MemStore store plantation-id plantation-data]
  (swap! (:plantations store) assoc plantation-id plantation-data)
  plantation-data)
