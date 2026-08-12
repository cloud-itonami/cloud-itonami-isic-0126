(ns oleaginousops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had no
  demo page and no generator. This namespace drives the REAL actor stack
  -- `oleaginousops.advisor` -> `oleaginousops.governor` ->
  `oleaginousops.phase` -> `oleaginousops.operation/run-operation`,
  against a real `oleaginousops.store/mem-store` -- and renders the page
  from the audit facts that stack actually produced.

  There is deliberately NO hand-written HTML data here: every plantation
  block on the page is read back out of the live Store, every
  disposition / hold reason / violation detail / confidence / basis comes
  out of `governor/check` and `phase/gate`, and even the action-gate and
  phase-gate reference tables are derived from the real vars
  (`governor/known-ops`, `governor/blocked-ops`,
  `governor/always-escalate-ops`, `governor/confidence-floor`,
  `facts/supply-categories`) or computed by actually calling
  `phase/gate`. If the Governor's rules change, this page changes with
  them.

  This repo's `oleaginousops.operation/build` is the synchronous
  flow (its docstring records that the langgraph-clj StateGraph wiring
  is deferred, mirroring `berrynutops.operation`), so this renderer
  drives that entry point rather than `langgraph.graph/run*`.

  DETERMINISM: no timestamps, no random ids, no wall-clock reads. The
  Store here needs no clock (`store/MemStore` holds only plantation
  records); if it ever does, the epoch-ms must be passed in from the
  caller rather than read here. Sets are sorted before rendering so map/
  set iteration order cannot leak into the bytes. Two consecutive runs
  are byte-identical.

  INVARIANT: `-main` refuses to write the file if the resulting ledger
  contains zero `:governor-hold` facts. A console that cannot show a
  HARD hold is not evidence that the Governor works, so the requirement
  is enforced at build time rather than left as a convention.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [jp-go-dds.skin]
            [oleaginousops.advisor :as advisor]
            [oleaginousops.facts :as facts]
            [oleaginousops.governor :as governor]
            [oleaginousops.operation :as operation]
            [oleaginousops.phase :as phase]
            [oleaginousops.store :as store]))

;; --------------------------- seeded store ---------------------------

(def ^:private seeded-plantations
  "The plantation blocks registered in the Store before the scenario
  runs. Registration is this actor's minimal unit of authority -- an
  unregistered block is a HARD violation (`:plantation-not-registered`),
  which is exactly what `plantation-909` below is used to demonstrate.
  `:fruit-class` values are ids from `facts/fruit-classes`."
  [["plantation-001" {:id "plantation-001"
                      :name "Sungai Merah Estate — Block A"
                      :fruit-class "oil-palm"
                      :hectares 128}]
   ["plantation-002" {:id "plantation-002"
                      :name "Tanjung Bakau Smallholder Group — Block 2"
                      :fruit-class "coconut"
                      :hectares 34}]
   ["plantation-003" {:id "plantation-003"
                      :name "Kalamata Terrace — Grove 7"
                      :fruit-class "olive"
                      :hectares 12}]
   ["plantation-004" {:id "plantation-004"
                      :name "Waimea Kukui Stand — Block C"
                      :fruit-class "candlenut"
                      :hectares 9}]])

(def ^:private unregistered-plantation
  "Never added to the Store -- referenced by one scenario step so the
  console can show the `:plantation-not-registered` HARD hold."
  "plantation-909")

(defn seed-store
  "Build a fresh in-memory Store with `seeded-plantations` registered."
  []
  (store/mem-store {:initial-plantations (into {} seeded-plantations)}))

;; ------------------------- probe advisor ---------------------------

(defrecord DirectExecutionAdvisor []
  advisor/Advisor
  (-advise [_advisor _store request]
    ;; A deliberately misbehaving advisor: it proposes a
    ;; perfectly ordinary, allowlisted op against a *registered* block,
    ;; with high confidence -- but asks to EXECUTE rather than propose.
    ;; The Advisor protocol is a swap seam (`operation/run-operation`
    ;; takes `:advisor`), so this is a real alternate implementation of
    ;; the real protocol, not a stub of the governor. It exists to prove
    ;; the `:no-execution` HARD rule fires on advisor output the mock
    ;; advisor can never produce.
    {:op (:op request)
     :effect :execute
     :count (:count request 0)
     :value {:plantation-id (:plantation-id request)
             :record-type (:record-type request "harvest")
             :count (:count request 0)}
     :cites ["advisor-self-authorized"]
     :summary "Advisor attempts to write the plantation record directly"
     :confidence 0.99}))

(defn direct-execution-advisor []
  (DirectExecutionAdvisor.))

;; ---------------------------- scenario ------------------------------

(def ^:private operator
  {:actor-id "oleaginous-ops-01" :role :plantation-operator})

(def ^:private scenario
  "One entry per actor run. `:request` and `:phase` are the caller's
  input; everything rendered from a run is the actor's output.

  Coverage: three clean auto-commits, three human escalations (one
  always-escalate crop-health flag, one over-threshold supply order, one
  phase-0 simulation gate), and five HARD holds -- one per hard rule in
  `oleaginousops.governor` -- which never reach a human."
  [{:label "harvest yield logged"
    :phase :phase-3
    :request {:op :log-plantation-record
              :plantation-id "plantation-001"
              :record-type "harvest"
              :count 4820
              :notes "FFB bunches, week 14"}}

   {:label "pruning round scheduled"
    :phase :phase-2
    :request {:op :schedule-field-operation
              :plantation-id "plantation-002"
              :requested-date "2026-09-02"
              :operation-type "pruning"}}

   {:label "fertilizer order under threshold"
    :phase :phase-3
    :request {:op :order-supplies
              :plantation-id "plantation-003"
              :category "fertilizer"
              :cost 420}}

   {:label "ganoderma suspected"
    :phase :phase-3
    :request {:op :flag-crop-health-concern
              :plantation-id "plantation-001"
              :concern "ganoderma basal stem rot suspected on 3 palms"}}

   {:label "seedling order over threshold"
    :phase :phase-3
    :request {:op :order-supplies
              :plantation-id "plantation-004"
              :category "seedling"
              :cost 1250}}

   {:label "oil-content test logged during simulation rollout"
    :phase :phase-0
    :request {:op :log-plantation-record
              :plantation-id "plantation-002"
              :record-type "oil-content-test"
              :count 63
              :notes "lab assay, copra sample"}}

   {:label "record against an unregistered block"
    :phase :phase-3
    :request {:op :log-plantation-record
              :plantation-id unregistered-plantation
              :record-type "harvest"
              :count 900}}

   {:label "record with a non-positive quantity"
    :phase :phase-3
    :request {:op :log-plantation-record
              :plantation-id "plantation-001"
              :record-type "harvest"
              :count 0
              :notes "operator typo"}}

   {:label "advisor asks to run the harvester itself"
    :phase :phase-3
    :request {:op :operate-field-equipment
              :plantation-id "plantation-001"}}

   {:label "advisor asks to finalize a spray decision"
    :phase :phase-3
    :request {:op :finalize-spray-application
              :plantation-id "plantation-002"}}

   {:label "op outside the closed allowlist"
    :phase :phase-3
    :request {:op :archive-plantation
              :plantation-id "plantation-003"}}

   {:label "advisor proposes :execute instead of :propose"
    :phase :phase-3
    :advisor :direct-execution
    :request {:op :log-plantation-record
              :plantation-id "plantation-004"
              :record-type "planting"
              :count 610}}])

(defn run-scenario!
  "Runs every `scenario` step through the real actor against a freshly
  seeded Store. Returns `{:store st :runs [...] :ledger [...]}` where
  each run carries the actor's own `:disposition`, `:audit`, `:record`
  and `:verdict`, and `:ledger` is every audit fact in the order the
  actor emitted them."
  []
  (let [st (seed-store)
        runs (reduce
              (fn [acc {:keys [phase request advisor] :as step}]
                (let [opts (when (= :direct-execution advisor)
                             {:advisor (direct-execution-advisor)})
                      context (assoc operator :phase phase)
                      result (operation/run-operation st request context opts)]
                  (conj acc (assoc step :context context :result result))))
              []
              scenario)]
    {:store st
     :runs runs
     :ledger (vec (mapcat #(get-in % [:result :audit]) runs))}))

;; ---------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v]
  (if (keyword? v) (name v) (str v)))

(defn- basis-str [basis]
  (str/join ", " (map kw basis)))

(defn- disposition-cell [disposition]
  (case disposition
    :commit   "<span class=\"ok\">committed</span>"
    :escalate "<span class=\"warn\">escalated to human</span>"
    :hold     "<span class=\"critical\">HARD hold</span>"
    (str "<span class=\"muted\">" (esc (kw disposition)) "</span>")))

(defn- outcome-detail
  "The actor's own explanation of what happened, pulled out of the
  disposition fact and verdict it emitted (never re-derived here).

  NOTE on `:escalate`: `oleaginousops.operation` collapses the
  Governor's `high-cost?` and `always-escalate?` signals into one
  `:high-stakes?` flag and then reports BOTH as reason
  `:always-escalate`, so an over-threshold supply order shows that reason
  too. That is the actor's real output; the verdict fields are printed
  next to it rather than the reason being silently rewritten here."
  [{:keys [disposition audit verdict]}]
  (let [f (last audit)]
    (case disposition
      :hold (str/join "<br>"
                      (map (fn [v]
                             (str "<code>" (esc (kw (:rule v))) "</code> &middot; "
                                  (esc (:detail v))))
                           (:violations f)))
      :escalate (str "<code>" (esc (kw (:reason f))) "</code>"
                     " &middot; confidence " (format "%.2f" (double (:confidence verdict)))
                     (when (:high-stakes? verdict) " &middot; high-stakes"))
      :commit (str "basis: " (esc (basis-str (:basis f))))
      "")))

(defn- run-row [{:keys [label phase request result]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc label)
          (esc (kw (:op request)))
          (esc (:plantation-id request))
          (esc (kw phase))
          (disposition-cell (:disposition result))
          (outcome-detail result)))

(defn- plantation-row [st id]
  (let [{:keys [name fruit-class hectares]} (store/registered-plantation st id)
        fc (facts/fruit-class-by-id fruit-class)]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc id) (esc name)
            (esc (:name fc)) (esc (kw (:group fc)))
            (esc hectares))))

(defn- ledger-row [f]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc (kw (:t f)))
          (esc (kw (:op f)))
          (esc (or (:subject f) (:plantation-id f)))
          (esc (if-let [c (:confidence f)] (format "%.2f" (double c)) ""))
          (esc (cond
                 (seq (:basis f)) (basis-str (:basis f))
                 (:reason f) (kw (:reason f))
                 (:proposal-summary f) (:proposal-summary f)
                 :else ""))))

(defn- gate-cell
  "Derived from the Governor's own vars -- not a hand-written
  description of them."
  [op]
  (cond
    (contains? governor/blocked-ops op)
    "<span class=\"critical\">HARD block, permanent &middot; never escalates, never overrides</span>"

    (contains? governor/always-escalate-ops op)
    "<span class=\"warn\">ALWAYS human sign-off, even when the Governor is clean</span>"

    (= :order-supplies op)
    (str "<span class=\"warn\">escalates above the category cost threshold ("
         (str/join ", "
                   (map (fn [[id c]]
                          (str (esc id) " " (:cost-threshold c)))
                        (sort-by key facts/supply-categories)))
         "; default " facts/default-cost-threshold ")</span>")

    :else
    (str "<span class=\"ok\">commits when the Governor is clean and confidence &ge; "
         (format "%.2f" (double governor/confidence-floor))
         "</span>")))

(defn- action-gate-rows []
  (for [op (sort-by name governor/all-recognized-ops)]
    (format "        <tr><td><code>%s</code></td><td>%s</td></tr>"
            (esc (kw op)) (gate-cell op))))

(defn- phase-gate-rows
  "Computed by actually calling `phase/gate` for each phase against a
  routine op and an always-escalate op."
  []
  (for [ph [:phase-0 :phase-1 :phase-2 :phase-3]]
    (let [routine (phase/gate ph {:op :log-plantation-record} :commit)
          health (phase/gate ph {:op :flag-crop-health-concern} :commit)]
      (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
              (esc (kw ph))
              (str (disposition-cell (:disposition routine))
                   (when-let [r (:reason routine)]
                     (str " <code>" (esc (kw r)) "</code>")))
              (str (disposition-cell (:disposition health))
                   (when-let [r (:reason health)]
                     (str " <code>" (esc (kw r)) "</code>")))))))

(defn- committed-record-rows [runs]
  (for [{:keys [request result]} runs
        :when (:record result)
        :let [{:keys [effect path value]} (:record result)]]
    (format "        <tr><td><code>%s</code></td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
            (esc (kw (:op request)))
            (esc (kw effect))
            (esc (str/join "/" path))
            (esc (pr-str (into (sorted-map) value))))))

(defn render
  "Renders the whole operator console from the result of
  `run-scenario!`."
  [{:keys [store runs ledger]}]
  (let [holds (filter #(= :governor-hold (:t %)) ledger)
        commits (filter #(= :committed (:t %)) ledger)
        escalations (filter #(= :approval-requested (:t %)) ledger)]
    (str
     "<!doctype html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-0126 &middot; oleaginous-fruit plantation operations</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Growing of oleaginous fruits (ISIC 0126) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · back-office coordination only — field-equipment operation and spray-application decisions are permanently blocked</span>\n"
     "</header>\n"
     "<main>\n"

     "  <section class=\"card\">\n"
     "    <h2>Run summary</h2>\n"
     "    <p class=\"muted\">Build-time-generated from the real actor stack "
     "(<code>oleaginousops.advisor</code> → <code>oleaginousops.governor</code> → "
     "<code>oleaginousops.phase</code> → <code>oleaginousops.operation/run-operation</code>) "
     "by <code>clojure -M:dev:render-html</code>. Nothing on this page is hand-written data.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Actor runs</th><th>Audit facts</th><th>Committed</th><th>Escalated to human</th><th>HARD holds</th></tr></thead>\n"
     "      <tbody>\n"
     (format "        <tr><td>%d</td><td>%d</td><td><span class=\"ok\">%d</span></td><td><span class=\"warn\">%d</span></td><td><span class=\"critical\">%d</span></td></tr>"
             (count runs) (count ledger) (count commits) (count escalations) (count holds))
     "\n      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Registered plantation blocks</h2>\n"
     "    <p class=\"muted\">Read back out of the live <code>oleaginousops.store</code>. A block must be registered here before any proposal referencing it can be considered — an unregistered id is a HARD violation, not a warning.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Block</th><th>Name</th><th>Oil crop</th><th>Group</th><th>ha</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial plantation-row store) (map first seeded-plantations))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Scenario — what the actor decided</h2>\n"
     "    <p class=\"muted\">Every disposition and every reason below is the actor's own output for that request. HARD holds never reach a human; escalations do. Observed here: <code>oleaginousops.operation</code> folds the Governor's cost gate and its always-escalate gate into one <code>:high-stakes?</code> flag, so an over-threshold supply order is reported with reason <code>always-escalate</code> as well — the reason is shown as emitted, not rewritten.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Step</th><th>Op</th><th>Block</th><th>Phase</th><th>Disposition</th><th>Actor's reason</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map run-row runs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Action gate (Oleaginous-Fruit Plantation Operations Governor)</h2>\n"
     "    <p class=\"muted\">Derived from <code>governor/all-recognized-ops</code>, <code>governor/blocked-ops</code>, <code>governor/always-escalate-ops</code>, <code>governor/confidence-floor</code> and <code>facts/supply-categories</code> — if the rules change, this table changes with them.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (action-gate-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Rollout phase gate</h2>\n"
     "    <p class=\"muted\">Computed by calling <code>oleaginousops.phase/gate</code> for each phase with an otherwise-committable verdict.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Phase</th><th>Routine op (<code>:log-plantation-record</code>)</th><th>Always-escalate op (<code>:flag-crop-health-concern</code>)</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (phase-gate-rows)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Committed records</h2>\n"
     "    <p class=\"muted\">The commit payloads the actor actually produced. <code>:effect</code> is <code>propose</code> on every one of them — this actor never executes in the field.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Effect</th><th>Path</th><th>Value</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (committed-record-rows runs)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Every fact the actor emitted, in emission order — one advisor proposal plus one disposition fact per run.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Block</th><th>Confidence</th><th>Basis / reason</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     "</main>\n"
     "<footer class=\"footer\"><p class=\"muted\">Regenerate with <code>clojure -M:dev:render-html</code>. The generator refuses to write this file if the run produces no HARD hold.</p></footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [ledger runs] :as result} (run-scenario!)
        holds (filter #(= :governor-hold (:t %)) ledger)
        commits (filter #(= :committed (:t %)) ledger)]
    (when (zero? (count holds))
      (throw (ex-info
              (str "refusing to write " out
                   ": the scenario produced ZERO :governor-hold facts. "
                   "An operator console that cannot show a HARD hold is not "
                   "evidence that the Governor can reject anything. Fix the "
                   "scenario (or the Governor) before regenerating.")
              {:out out
               :runs (count runs)
               :ledger-facts (count ledger)
               :governor-holds 0})))
    (spit out (render result))
    (println "wrote" out "-" (count runs) "actor runs,"
             (count ledger) "audit facts,"
             (count commits) "committed,"
             (count holds) "HARD holds")))
