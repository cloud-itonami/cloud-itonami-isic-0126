(ns oleaginousops.registry
  "Pure validation functions for oleaginous-fruit plantation operations.
  These are called by the Governor to independently verify proposal
  parameters -- the LLM advisor's confidence is NOT sufficient to
  override these checks. Mirrors `berrynutops.registry`
  (cloud-itonami-isic-0125) in shape.")

(defn cost-exceeds-threshold?
  "Independently verify a proposed spend against its category/default
  threshold. Inclusive at the boundary (exactly-at-threshold does not
  escalate)."
  [cost threshold]
  (> cost threshold))

(defn plantation-count-non-positive?
  "A logged plantation-record quantity (trees counted, harvest weight,
  oil-content-test yield, bunch/fruit count) of zero or negative is not a
  real observation -- reject it as a HARD violation rather than silently
  accepting bad data into the record."
  [count]
  (<= count 0))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))
