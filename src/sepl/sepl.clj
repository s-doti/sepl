(ns sepl.sepl
  (:require [clojure.core.async :refer [<!! <!]]
            [seamless-async.core :refer [go-go? *async?*]]
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

  (go-go? *async?*

          (try
            (loop [[{:keys [type args depth] :as step}
                    & more-steps :as steps] (apply list steps)
                   state state
                   tracker (utils/update-tracker)
                   ts (utils/current-time-ms)]

              ;interrupted flow
              (when (.isInterrupted (Thread/currentThread))
                (throw (new InterruptedException)))

              ;debug/forensic.. stuff
              (if-not depth
                (utils/report-done tracker)                 ;topmost branch done
                (when (and (< 10000                         ;long step side flow
                              (- (utils/current-time-ms)
                                 (:start-time tracker (utils/current-time-ms))))
                           (zero? (mod (:iterations tracker 1) 1e4)))
                  (utils/report-long-step step more-steps tracker io)))

              ;safety measures
              (utils/assert-sepl-computation-thresholds tracker max-iterations max-depth max-duration)

              ;let's get to work
              (if (nil? step)

                ;we're done done. return state
                state

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
                      ;park/block if necessary
                      chan? (instance? ManyToManyChannel state)
                      state (cond-> state
                                    (and chan? *async?*) <!
                                    (and chan? (not *async?*)) <!!)]

                  ;throw if state was ruined by exception
                  (if (instance? Throwable state)
                    (throw state)

                    ;move on to execute step logic
                    (let [{:keys [side-effect-fn process-fn]} (get flows type)
                          args' (->deser-args args)
                          ->verbosely-fail (partial utils/verbosely-fail type args)]

                      ;side-effect execution
                      (let [outcome (when side-effect-fn
                                      ((->verbosely-fail true side-effect-fn)
                                       state
                                       args'))
                            ;park/block if necessary
                            chan? (instance? ManyToManyChannel outcome)
                            outcome' (cond-> outcome
                                             (and chan? *async?*) <!
                                             (and chan? (not *async?*)) <!!)]

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

                            ;debugger integration
                            (when io
                              (debugger-io io step outcome' new-steps more-steps))

                            ;loopy-loop
                            (recur steps' state' tracker' (utils/current-time-ms))))))))))

            (catch Throwable t
              (if *async?*
                t
                (throw t))))))
