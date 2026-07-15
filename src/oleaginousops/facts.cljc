(ns oleaginousops.facts
  "Reference facts for oleaginous-fruit plantation operations coordination:
  supply category cost policy and oil-bearing fruit/nut species
  classification. This namespace contains pure lookup functions for
  domain reference data -- the Governor and Advisor consult these instead
  of inventing thresholds. Mirrors `berrynutops.facts`
  (cloud-itonami-isic-0125) in shape.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (grower/plantation-manager)."
  {"seedling"
   {:id "seedling" :name "苗木" :cost-threshold 500}

   "fertilizer"
   {:id "fertilizer" :name "肥料" :cost-threshold 500}

   "equipment"
   {:id "equipment" :name "設備" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def fruit-classes
  "End-use classes this actor's plantation/block records may cover (ISIC
  0126: growing of oleaginous fruits -- fruit and nuts grown mainly for
  oil extraction). Palm-family oil crops: oil palm, coconut. Drupe oil
  crops: olive. Other oil-bearing fruit trees: candlenut (kukui)."
  {"oil-palm"  {:id "oil-palm"  :name "アブラヤシ"     :group :palm}
   "coconut"   {:id "coconut"   :name "ココナッツ"     :group :palm}
   "olive"     {:id "olive"     :name "オリーブ"       :group :drupe}
   "candlenut" {:id "candlenut" :name "ククイナッツ"   :group :nut}})

(defn fruit-class-by-id [id]
  (get fruit-classes id))
