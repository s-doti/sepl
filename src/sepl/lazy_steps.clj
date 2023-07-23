(ns sepl.lazy-steps
  (:require [clojure.core.async :refer [<!!]]
            [sepl.utils :as utils]
            [sepl.debugger :refer [debugger-io]])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))

(defn sepl [flows state steps
            & {:keys [->ser-args ->deser-args
                      max-iterations max-depth max-duration
                      with-optimizer with-checkpoint io]
               :or   {->ser-args     identity
                      ->deser-args   identity
                      max-iterations Integer/MAX_VALUE
                      max-depth      Integer/MAX_VALUE
                      max-duration   Integer/MAX_VALUE}}]

  (letfn [(unfold [[{:keys [type args depth] :as step}
                    & more-steps :as steps]
                   state
                   tracker
                   ts]

            ;debug/forensic.. stuff
            (if-not depth
              (utils/report-done tracker)                   ;topmost branch done
              (when (and (< 10000                           ;long step side flow
                            (- (utils/current-time-ms)
                               (:start-time tracker (utils/current-time-ms))))
                         (zero? (mod (:iterations tracker 1) 1e4)))
                (utils/report-long-step step more-steps tracker io)))

            ;safety measures
            (utils/assert-sepl-computation-thresholds tracker max-iterations max-depth max-duration)

            (when step                                      ;otherwise we're done done here

              ;state mutating preparations
              (let [state (cond-> state
                                  ;enrich state with DR data (aka visible computation tree)
                                  with-checkpoint (with-checkpoint
                                                    (->> steps
                                                         (mapv #(select-keys % [:type :args]))
                                                         (delay)))
                                  ;todo arbitrary heuristic - worth it?
                                  with-optimizer (with-optimizer
                                                   (->> steps
                                                        (take 100)
                                                        (map :args))))
                    ;block if necessary
                    state (cond-> state
                                  (instance? ManyToManyChannel state) <!!)]

                ;throw if state was ruined by exception
                (if (instance? Throwable state)
                  (throw state)

                  ;commence step logic
                  (let [{:keys [side-effect-fn process-fn]} (get flows type)
                        args' (->deser-args args)
                        ->verbosely-fail (partial utils/verbosely-fail type args)]

                    ;side-effect execution
                    (let [outcome (when side-effect-fn
                                    ((->verbosely-fail true side-effect-fn)
                                     state
                                     args'))
                          ;block if necessary
                          outcome' (cond-> outcome
                                           (instance? ManyToManyChannel outcome) <!!)]

                      ;throw if outcome is exception
                      (if (instance? Throwable outcome')
                        (throw outcome')

                        ;process fn execution
                        (let [step' (assoc step :side-effect-outcome outcome' :timestamp ts)
                              new-steps (when process-fn
                                          ((->verbosely-fail false process-fn)
                                           args'
                                           outcome'
                                           step'))
                              steps' (if (not-empty new-steps)
                                       (utils/add-steps new-steps more-steps step' ->ser-args)
                                       more-steps)
                              ;side-effect-only flow must return a mutated state
                              state' (if process-fn state outcome')
                              tracker' (utils/update-tracker tracker step)]

                          ;debugger integration (kinda redundant for this use-case)
                          (when io
                            (debugger-io io step outcome' new-steps more-steps))

                          (cons {:step                step
                                 :steps               steps'
                                 :state               state'
                                 :tracker             tracker'
                                 :side-effect-outcome outcome'}
                                (lazy-seq
                                  (unfold steps' state' tracker' (utils/current-time-ms))))))))))))]
    (unfold (apply list steps)
            state
            (utils/update-tracker)
            (utils/current-time-ms))))
