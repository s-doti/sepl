(ns sepl.debugger)

(defn- break?
  "Returns true if step matches breakpoint data."
  [step {:keys [type args] :as breakpoint}]
  (and (or type (not-empty args))
       (or (nil? type) (= type (:type step)))
       (or (empty? args) (clojure.set/subset? (set args) (set (:args step))))))

(defn- collect-debug-data
  "Collect debug data."
  [step side-effect-outcome new-steps more-steps]
  {:step                step
   :side-effect-outcome side-effect-outcome
   :child-steps         new-steps
   :visible-branches    (mapv #(select-keys % [:type :args :depth]) more-steps)})

(defn- handle-debug-instruction
  "Update next breakpoint according to user debug instruction;
   Step-in: proceed further down the computation tree, or
            sideways if reached bottom.
   Step-over: proceed sideways in the computation tree.
   Step-out: proceed from nearest branch, above current, in the
             computation tree.
   Otherwise commence execution till done."
  [instruction io step new-steps more-steps]
  (case instruction
    :step-in (swap! io assoc :breakpoint (select-keys
                                           (or (last new-steps) (first more-steps))
                                           [:type :args]))
    :step-over (swap! io assoc :breakpoint (select-keys
                                             (first more-steps)
                                             [:type :args]))
    :step-out (swap! io assoc :breakpoint (select-keys
                                            (first (filter #(> (:depth step) (:depth %)) more-steps))
                                            [:type :args]))
    io))

(defn debugger-io
  "If a breakpoint is detected,
   and it matches provided step,
   then gather helpful debug data,
   and block for follow-up debug instruction."
  [io step side-effect-outcome new-steps more-steps]

  (let [{:keys [breakpoint resume]} (deref io)]
    (when (break? step breakpoint)

      ;send debug data out
      (swap! io merge (collect-debug-data step side-effect-outcome new-steps more-steps))

      ;await debug instruction
      (let [instruction (deref resume)]
        (handle-debug-instruction instruction io step new-steps more-steps)))))
