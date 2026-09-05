(ns oleaginousops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL OperationActor (`oleaginousops.operation/build` ->
  advisor -> `oleaginousops.governor/check` -> `oleaginousops.phase/gate`)
  over a REAL `oleaginousops.store/mem-store` plantation register, and
  renders whatever that produced. Nothing on the page is a hand-typed
  result:

    - every plantation-register row is read back out of the Store
      (`store/registered-plantation`, including for the subjects the
      demo deliberately does NOT register),
    - every run's disposition, reason, basis and confidence is the
      actor's own return value / audit fact,
    - every HARD-hold rule name and every violation detail string is the
      Governor's own `:violations` entry -- never a literal here,
    - the op-gate table's op sets, the confidence floor and the supply
      cost thresholds are read from `oleaginousops.governor` /
      `oleaginousops.facts` public vars,
    - the phase-gate table is computed by CALLING `phase/gate` for each
      phase, not transcribed from its docstring.

  Ledger provenance: unlike the sibling actors that persist to their
  Store, THIS repo's `store/Store` protocol is a plantation *register*
  only (`registered-plantation`) and `operation/run-operation` RETURNS
  its audit facts and its commit record rather than writing them. The
  `:ledger` below is therefore the in-order concatenation of the
  `:audit` vectors the real runs returned -- append-only, actor-produced,
  nothing synthesised.

  Subject provenance: every plantation-id driven below is either seeded
  into the Store by `plantation-seed` (`plantation-001`..`plantation-004`,
  all four `oleaginousops.facts/fruit-classes` ids) or is the
  deliberately UNREGISTERED `plantation-999`, whose only purpose is to
  make the Governor's `:plantation-not-registered` HARD invariant fire
  for real.

  Deterministic: no clock, no randomness, no network, no timestamps in
  the page. Re-running writes a byte-identical file.

  Run: `clojure -M:dev:render-html [out-file]`
  (default out-file `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [oleaginousops.advisor :as advisor]
            [oleaginousops.facts :as facts]
            [oleaginousops.governor :as governor]
            [oleaginousops.operation :as operation]
            [oleaginousops.phase :as phase]
            [oleaginousops.store :as store]))

;; ----------------------------- the seed -----------------------------

(def ^:private plantation-seed
  "Registered plantation blocks. One block per `facts/fruit-classes`
  entry, so every oil crop this vertical recognises (oil palm, coconut,
  olive, candlenut) is exercised by a real Store lookup."
  {"plantation-001" {:id "plantation-001" :name "Selangor Block A"
                     :fruit-class "oil-palm" :hectares 128.0}
   "plantation-002" {:id "plantation-002" :name "Davao Grove 2"
                     :fruit-class "coconut" :hectares 74.5}
   "plantation-003" {:id "plantation-003" :name "Kalamata Terrace"
                     :fruit-class "olive" :hectares 31.2}
   "plantation-004" {:id "plantation-004" :name "Kona Kukui Stand"
                     :fruit-class "candlenut" :hectares 12.8}})

(def ^:private unregistered-subject
  "Never seeded into the Store on purpose -- this is how the
  `:plantation-not-registered` HARD invariant is reached honestly."
  "plantation-999")

;; ------------------------- rogue advisors ---------------------------
;; `oleaginousops.advisor/Advisor` is an injection seam ("mock | real
;; LLM", see its docstring). Two of the Governor's paths cannot be
;; reached from a REQUEST alone, because the compliant MockAdvisor never
;; emits them: a non-`:propose` effect, and a sub-floor confidence. They
;; are exactly the paths that exist because the advisor is NOT trusted,
;; so they are exercised by swapping in deliberately non-compliant
;; advisors that delegate to the real MockAdvisor and then misbehave.
;; The hold/escalation those produce is still computed by the real
;; Governor -- nothing is appended by hand.

(defrecord ExecutingAdvisor []
  advisor/Advisor
  (-advise [_ store request]
    (assoc (advisor/-advise (advisor/mock-advisor) store request)
           :effect :execute)))

(defrecord UnsureAdvisor [confidence]
  advisor/Advisor
  (-advise [_ store request]
    (assoc (advisor/-advise (advisor/mock-advisor) store request)
           :confidence confidence)))

;; ----------------------------- the run ------------------------------

(def ^:private operator
  {:actor-id "ops-1" :actor-role :plantation-operator})

(defn- ctx [phase] (assoc operator :phase phase))

(def ^:private scenarios
  "One entry = one coordination request driven through the real actor.
  `:feeds` describes only the INPUT (what this request hands the
  Governor); the disposition, reason, basis and confidence columns of
  the rendered timeline all come back from the run itself."
  [{:tid "t01" :phase :phase-3
    :feeds "Routine harvest record against a registered oil-palm block, positive bunch count."
    :request {:op :log-plantation-record :plantation-id "plantation-001"
              :record-type "harvest" :count 4820 :notes "FFB bunches, week 31"}}

   {:tid "t02" :phase :phase-3
    :feeds "Pruning window on a registered coconut grove. Scheduling only -- no equipment is operated."
    :request {:op :schedule-field-operation :plantation-id "plantation-002"
              :operation-type "pruning" :requested-date "2026-08-19"}}

   {:tid "t03" :phase :phase-3
    :feeds "Fertilizer procurement at 420 units, under the fertilizer category threshold."
    :request {:op :order-supplies :plantation-id "plantation-003"
              :category "fertilizer" :cost 420}}

   {:tid "t04" :phase :phase-3
    :feeds "Equipment procurement at 1800 units, over the equipment category threshold."
    :request {:op :order-supplies :plantation-id "plantation-004"
              :category "equipment" :cost 1800}}

   {:tid "t05" :phase :phase-3
    :feeds "Seedling procurement at 900 units, over the seedling category threshold."
    :request {:op :order-supplies :plantation-id "plantation-001"
              :category "seedling" :cost 900}}

   {:tid "t06" :phase :phase-3
    :feeds "Crop-health concern (basal stem rot / Ganoderma) on the oil-palm block, advisor confidence 0.8."
    :request {:op :flag-crop-health-concern :plantation-id "plantation-001"
              :concern "basal stem rot (Ganoderma) suspected in rows 14-18"}}

   {:tid "t07" :phase :phase-0
    :feeds "The same Governor-clean harvest record as t01, but submitted while the rollout is still in simulation."
    :request {:op :log-plantation-record :plantation-id "plantation-002"
              :record-type "oil-content-test" :count 1200 :notes "copra moisture panel"}}

   {:tid "t08" :phase :phase-1
    :feeds "Crop-health concern on the olive terrace under supervised rollout."
    :request {:op :flag-crop-health-concern :plantation-id "plantation-003"
              :concern "drought stress across the upper terrace"}}

   {:tid "t09" :phase :phase-3
    :feeds "Harvest record for a plantation-id that was never registered in the Store."
    :request {:op :log-plantation-record :plantation-id unregistered-subject
              :record-type "harvest" :count 300 :notes "block unknown to the register"}}

   {:tid "t10" :phase :phase-3
    :feeds "Harvest record on a registered block with a logged quantity of 0."
    :request {:op :log-plantation-record :plantation-id "plantation-002"
              :record-type "harvest" :count 0 :notes "nil delivery claimed"}}

   {:tid "t11" :phase :phase-3
    :feeds "A request to operate field equipment directly on the oil-palm block."
    :request {:op :operate-field-equipment :plantation-id "plantation-001"
              :notes "raise the harvester boom"}}

   {:tid "t12" :phase :phase-3
    :feeds "A request to finalize a spray application on the olive terrace."
    :request {:op :finalize-spray-application :plantation-id "plantation-003"
              :notes "commit the copper spray decision"}}

   {:tid "t13" :phase :phase-3
    :feeds "An op outside the closed allowlist (land clearing approval)."
    :request {:op :approve-land-clearing :plantation-id "plantation-004"
              :notes "clear the adjoining parcel"}}

   {:tid "t14" :phase :phase-3 :advisor (->ExecutingAdvisor)
    :feeds "A Governor-clean harvest record, but the advisor is swapped for one that returns :effect :execute."
    :request {:op :log-plantation-record :plantation-id "plantation-001"
              :record-type "harvest" :count 900 :notes "advisor attempts direct actuation"}}

   {:tid "t15" :phase :phase-3 :advisor (->UnsureAdvisor 0.45)
    :feeds "A routine scheduling request, but the advisor is swapped for one that reports confidence 0.45."
    :request {:op :schedule-field-operation :plantation-id "plantation-004"
              :operation-type "harvest" :requested-date "2026-09-02"}}

   {:tid "t16" :phase :phase-3
    :feeds "Two invariants at once: field-equipment operation AND an unregistered plantation-id."
    :request {:op :operate-field-equipment :plantation-id unregistered-subject
              :notes "boom raise on an unknown block"}}

   {:tid "t17" :phase :phase-9
    :feeds "The same Governor-clean pruning request as t02, submitted under a phase the gate does not recognise."
    :request {:op :schedule-field-operation :plantation-id "plantation-002"
              :operation-type "pruning" :requested-date "2026-08-26"}}])

(defn run-demo!
  "Seeds a real `store/mem-store`, builds the real OperationActor for
  each scenario (swapping the injected advisor where the scenario asks
  for one) and runs every request through advisor -> governor -> phase
  gate. Returns `{:store st :runs [...] :ledger [...]}` where `:runs`
  carries the actor's own `{:disposition :audit :record :verdict}` per
  request and `:ledger` is the in-order concatenation of the audit facts
  those runs produced."
  []
  (let [st (store/mem-store {:initial-plantations plantation-seed})
        runs (mapv (fn [{:keys [tid phase request advisor] :as sc}]
                     (let [actor (operation/build st (when advisor {:advisor advisor}))
                           result (actor request (ctx phase))]
                       (assoc sc :tid tid :result result)))
                   scenarios)]
    {:store st
     :runs runs
     :ledger (vec (mapcat #(-> % :result :audit) runs))}))

(defn- holds [ledger]
  (filterv #(= :governor-hold (:t %)) ledger))

(defn- hard-holds [ledger]
  (filterv #(seq (:violations %)) (holds ledger)))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-str [v] (if (keyword? v) (name v) (str v)))

(defn- basis-str [basis]
  (if (seq basis) (str/join ", " (map kw-str basis)) ""))

(defn- fact-subject [f] (or (:subject f) (:plantation-id f)))

(defn- disposition-cell [d]
  (case d
    :commit "<span class=\"ok\">commit</span>"
    :escalate "<span class=\"warn\">escalate &middot; human sign-off</span>"
    :hold "<span class=\"critical\">HOLD</span>"
    (str "<span class=\"muted\">" (esc (kw-str d)) "</span>")))

(defn- hold-reason [{:keys [audit]}]
  (let [f (last audit)]
    (cond
      ;; plain "·" (not the &middot; entity): this string is escaped by
      ;; `esc` on its way into the cell.
      (seq (:violations f)) (str "HARD · " (basis-str (:basis f)))
      (:phase-reason f) (str "phase gate · " (kw-str (:phase-reason f)))
      :else "")))

(defn- run-reason [{:keys [disposition audit] :as result}]
  (case disposition
    :hold (hold-reason result)
    :escalate (kw-str (:reason (last audit)))
    :commit (basis-str (:basis (last audit)))
    ""))

;; --- plantation register (real Store lookups) ---

(defn- register-rows [st ledger]
  (let [seeded (set (keys @(:plantations st)))
        referenced (set (keep fact-subject ledger))
        ids (sort (into seeded referenced))]
    (str/join "\n"
      (for [id ids
            :let [rec (store/registered-plantation st id)
                  fc (some-> rec :fruit-class facts/fruit-class-by-id)
                  touched (count (filter #(= id (fact-subject %)) ledger))]]
        (str "        <tr><td><code>" (esc id) "</code></td><td>"
             (esc (or (:name rec) "—")) "</td><td>"
             (if fc (str (esc (:name fc)) " (" (esc (:id fc)) ", " (esc (kw-str (:group fc))) ")") "—")
             "</td><td class=\"num\">" (if rec (esc (:hectares rec)) "—") "</td><td>"
             (if rec
               "<span class=\"ok\">registered</span>"
               "<span class=\"critical\">NOT registered &middot; HARD invariant</span>")
             "</td><td class=\"num\">" touched "</td></tr>")))))

;; --- op gate contract ---

(def ^:private op-notes
  ;; STATIC hand-written description of this actor's FIXED op contract
  ;; (README "Operational requests" + the governor docstring). This is
  ;; the only hand-authored content on the page. It is documentation of
  ;; fixed behaviour, not telemetry -- the op SETS, the confidence floor
  ;; and the cost thresholds beside it are all read from the governor /
  ;; facts vars below, and every observed outcome comes from the run.
  {:log-plantation-record "Record planting / harvest-yield / oil-content-test data. The logged quantity is independently re-verified by oleaginousops.registry."
   :schedule-field-operation "Propose a pruning / spraying / harvest window. Never makes or finalizes a spray-application decision."
   :flag-crop-health-concern "Surface a pest / disease (bud rot, Ganoderma) or drought-stress concern for agronomist review."
   :order-supplies "Procurement for seedlings, fertilizer or equipment, against the category cost threshold."
   :operate-field-equipment "Direct operation of field equipment — the grower's exclusive authority."
   :finalize-spray-application "Finalizing a spray-application decision — the agronomist's exclusive authority."})

(defn- gate-label [op]
  (cond
    (contains? governor/blocked-ops op)
    "<span class=\"critical\">permanently blocked &middot; HARD, never overridable</span>"
    (contains? governor/always-escalate-ops op)
    "<span class=\"warn\">ALWAYS human sign-off, at every phase</span>"
    :else
    "<span class=\"ok\">may commit when the Governor is clean and the phase gate allows</span>"))

(defn- op-gate-rows []
  (str/join "\n"
    (for [op (sort-by name governor/all-recognized-ops)]
      (str "        <tr><td><code>:" (esc (name op)) "</code></td><td>"
           (gate-label op) "</td><td>" (esc (get op-notes op "—")) "</td></tr>"))))

(defn- threshold-rows []
  (str/join "\n"
    (for [[id c] (sort-by key facts/supply-categories)]
      (str "        <tr><td><code>" (esc id) "</code></td><td>" (esc (:name c))
           "</td><td class=\"num\">" (esc (:cost-threshold c)) "</td></tr>"))))

;; --- phase gate (computed by calling the real gate) ---

(def ^:private phase-ids
  ;; The four declared rollout phases, plus one deliberately unknown
  ;; phase id so the gate's fail-closed default is shown by running it.
  [:phase-0 :phase-1 :phase-2 :phase-3 :phase-9])

(defn- gate-cell [ph req d]
  (let [{:keys [disposition reason]} (phase/gate ph req d)]
    (str (disposition-cell disposition)
         (when reason (str " <span class=\"muted\">" (esc (kw-str reason)) "</span>")))))

(defn- phase-rows []
  (let [routine {:op :log-plantation-record}
        always {:op :flag-crop-health-concern}]
    (str/join "\n"
      (for [ph phase-ids]
        (str "        <tr><td><code>" (esc (kw-str ph)) "</code></td><td>"
             (gate-cell ph routine :commit) "</td><td>"
             (gate-cell ph always :commit) "</td><td>"
             (gate-cell ph routine :hold) "</td></tr>")))))

;; --- run timeline ---

(defn- run-row [{:keys [tid phase request advisor feeds result]}]
  (str "        <tr><td><code>" (esc tid) "</code></td><td><code>:"
       (esc (name (:op request))) "</code></td><td><code>"
       (esc (:plantation-id request)) "</code></td><td><code>"
       (esc (kw-str phase)) "</code>"
       (when advisor
         (str "<br><span class=\"muted\">advisor: "
              (esc (.getSimpleName (class advisor))) "</span>"))
       "</td><td>" (disposition-cell (:disposition result))
       "</td><td>" (esc (run-reason result))
       "</td><td class=\"num\">" (esc (get-in result [:verdict :confidence]))
       "</td><td class=\"muted\">" (esc feeds) "</td></tr>"))

;; --- hard hold detail ---

(defn- violation-rows [runs]
  (str/join "\n"
    (for [{:keys [tid result]} runs
          :let [f (last (:audit result))]
          :when (= :governor-hold (:t f))
          v (or (seq (:violations f))
                [{:rule (or (:phase-reason f) :hold)
                  :detail "no Governor violation — held by the rollout phase gate"}])]
      (str "        <tr><td><code>" (esc tid) "</code></td><td><code>"
           (esc (fact-subject f)) "</code></td><td><code>:"
           (esc (kw-str (:rule v))) "</code></td><td>" (esc (:detail v)) "</td></tr>"))))

;; --- ledger ---

(defn- ledger-row [{:keys [t op summary proposal-summary reason confidence basis] :as f}]
  (str "        <tr><td>" (esc (kw-str t)) "</td><td><code>:"
       (esc (kw-str (or op :n-a))) "</code></td><td><code>"
       (esc (fact-subject f)) "</code></td><td class=\"num\">"
       (if (some? confidence) (esc confidence) "—") "</td><td>"
       (esc (or (not-empty (basis-str basis))
                (some-> reason kw-str)
                summary
                proposal-summary
                ""))
       "</td></tr>"))

;; --- committed records ---

(defn- record-rows [runs]
  (str/join "\n"
    (for [{:keys [tid result]} runs
          :when (:record result)
          :let [r (:record result)]]
      (str "        <tr><td><code>" (esc tid) "</code></td><td><code>"
           (esc (kw-str (:effect r))) "</code></td><td><code>"
           (esc (str/join "/" (:path r))) "</code></td><td><code>"
           (esc (pr-str (:value r))) "</code></td></tr>"))))

(defn render
  "Renders the whole operator-console document from the result of
  `run-demo!` (or any other real run of this actor)."
  [{:keys [store runs ledger]}]
  (let [hs (holds ledger)
        hh (hard-holds ledger)
        by-disp (frequencies (map #(get-in % [:result :disposition]) runs))]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-0126 &middot; oleaginous-fruit plantation operations</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Growing of oleaginous fruits (ISIC 0126) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · field equipment and spray decisions permanently out of scope</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>This run</h2>\n"
     "    <p class=\"muted\">Generated at build time by <code>oleaginousops.render-html</code> (<code>clojure -M:dev:render-html</code>): every figure below is the actor's own output, produced by driving <code>oleaginousops.operation/build</code> over a real <code>oleaginousops.store/mem-store</code>. No clock, no randomness, no network — re-running writes a byte-identical file.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Requests driven</th><th>Audit facts</th><th>Commits</th><th>Escalations</th><th>Holds</th><th>HARD holds</th></tr></thead>\n"
     "      <tbody>\n"
     "        <tr><td class=\"num\">" (count runs) "</td><td class=\"num\">" (count ledger)
     "</td><td class=\"num\">" (get by-disp :commit 0)
     "</td><td class=\"num\">" (get by-disp :escalate 0)
     "</td><td class=\"num\">" (count hs)
     "</td><td class=\"num\">" (count hh) "</td></tr>\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Plantation register</h2>\n"
     "    <p class=\"muted\">Read back out of the Store with <code>store/registered-plantation</code> — including for every plantation-id this run referenced. A block that is not on this register cannot be acted on: the Governor's <code>:plantation-not-registered</code> invariant is a HARD hold.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Plantation block</th><th>Name</th><th>Oil crop</th><th>ha</th><th>Register</th><th>Facts this run</th></tr></thead>\n"
     "      <tbody>\n"
     (register-rows store ledger) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Op gate (Oleaginous Operations Governor)</h2>\n"
     "    <p class=\"muted\">Op sets read from <code>oleaginousops.governor</code> (<code>known-ops</code>, <code>blocked-ops</code>, <code>always-escalate-ops</code>). Any op outside this table is an <code>:op-not-allowed</code> HARD hold. Confidence floor <span class=\"num\">"
     (esc governor/confidence-floor)
     "</span> — a proposal below it escalates, and the Governor recomputes cost and logged quantity itself rather than trusting the advisor.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th><th>Scope</th></tr></thead>\n"
     "      <tbody>\n"
     (op-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <h3>Supply cost thresholds</h3>\n"
     "    <p class=\"muted\">From <code>oleaginousops.facts/supply-categories</code>. An order above its threshold escalates; an uncategorised order falls back to <span class=\"num\">"
     (esc facts/default-cost-threshold) "</span>.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Category</th><th>Name</th><th>Escalation threshold</th></tr></thead>\n"
     "      <tbody>\n"
     (threshold-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Rollout phase gate</h2>\n"
     "    <p class=\"muted\">Each cell is computed by calling <code>oleaginousops.phase/gate</code> for that phase, not transcribed. A HARD hold passes through every phase unchanged — the phase gate can only tighten a decision, never loosen one — and an unrecognised phase fails closed.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Phase</th><th>Governor-clean routine op</th><th>Governor-clean always-escalate op</th><th>Governor HARD hold</th></tr></thead>\n"
     "      <tbody>\n"
     (phase-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Run timeline</h2>\n"
     "    <p class=\"muted\">One row = one request driven through the real actor. The last column states only what the request fed the Governor; disposition, reason and confidence are the run's own result.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Op</th><th>Plantation</th><th>Phase</th><th>Disposition</th><th>Reason / basis</th><th>Conf.</th><th>What this request feeds</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map run-row runs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>HARD holds this run</h2>\n"
     "    <p class=\"muted\">Rule names and detail text are the Governor's own <code>:violations</code> entries, taken off the ledger fact. A HARD hold never reaches a human — there is nothing to approve.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Plantation</th><th>Rule</th><th>Governor detail</th></tr></thead>\n"
     "      <tbody>\n"
     (violation-rows runs) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Committed records</h2>\n"
     "    <p class=\"muted\">The commit records <code>operation/run-operation</code> returned for the runs that reached <code>:commit</code>. This repo's Store is a plantation register, not yet the record SSoT, so these are the actor's returned records rather than persisted rows — stated plainly rather than dressed up.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>#</th><th>Effect</th><th>Path</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     (record-rows runs) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log, in order: every advisor proposal and every disposition this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Plantation</th><th>Conf.</th><th>Basis / reason / summary</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer><p>cloud-itonami-isic-0126 · AGPL-3.0-or-later · generated from a real actor run, not a mock-up.</p></footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [ledger] :as result} (run-demo!)
        hs (holds ledger)
        hh (hard-holds ledger)]
    ;; Build-time invariant: a console that shows no real HARD hold is
    ;; not evidence of a governor.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count ledger)})))
    (when (empty? hh)
      (throw (ex-info "no :governor-hold fact carries governor :violations — refusing to write a console whose only holds are phase-gate holds"
                      {:ledger-facts (count ledger) :holds (count hs)})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, " (count hs) " holds, "
                  (count hh) " HARD holds)"))))
