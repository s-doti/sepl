(ns sepl.t-checkpoint
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]])
  (:import (java.io File)))

(background
  (around :facts
          (let [fs-path (File/createTempFile "checkpoint" "sepl")]
            (logger/with-level :info ?form))))

;DR demonstration (pause/resume would work just as well)

(midje.config/at-print-level
  :print-facts

  (facts
    "checkpointing"

    ;5!=120
    (let [flows {"calc-next" {:side-effect-fn (fn load [state args] state)
                              :process-fn     (fn calc-next [args outcome & [metacontext]]
                                                (when (pos? args)
                                                  (-> '()
                                                      (->step "set-next" (* args outcome))
                                                      (->step "calc-next" (dec args)))))}
                 "set-next"  {:side-effect-fn (fn store [state args] args)}}
          initial-state 1
          step (->step "calc-next" 5)
          checkpoint-handler (fn [state runtime-state]
                               ;(prn state (force runtime-state))
                               (spit fs-path [state (force runtime-state)])
                               state)]

      ;fail mid-way
      (fact "to be sure sepl crashes"
            (spit fs-path "")
            (sepl flows initial-state [step]
                       :max-iterations 3
                       :with-checkpoint checkpoint-handler) => (throws AssertionError))
      (let [interim (read-string (slurp fs-path))]
        (fact "sepl crashes with recoverable checkpoint"
              interim => [20 [{:args 3 :type "calc-next"}]]))

      (fact "to be sure lazy sepl crashes"
            (spit fs-path "")
            (:state (last (lazy-sepl flows initial-state [step]
                                     :max-iterations 3
                                     :with-checkpoint checkpoint-handler))) => (throws AssertionError))
      (let [interim (read-string (slurp fs-path))]
        (fact "lazy sepl crashes with recoverable checkpoint"
              interim => [20 [{:args 3 :type "calc-next"}]]))

      (fact "to be sure async sepl crashes"
            (spit fs-path "")
            (type (last (as-seq (async-sepl flows initial-state [step]
                                            :max-iterations 3
                                            :with-checkpoint checkpoint-handler)))) => AssertionError)
      (let [interim (read-string (slurp fs-path))]
        (fact "async sepl crashes with recoverable checkpoint"
              interim => [20 [{:args 3 :type "calc-next"}]]))

      ;resume from 'crash'
      (let [[state steps] (read-string (slurp fs-path))]

        (fact "sepl miraculously recovers from crash"
              (sepl flows state steps
                         :with-checkpoint checkpoint-handler) => 120)

        (fact "lazy sepl miraculously recovers from crash"
              (:state (last (lazy-sepl flows state steps
                                       :with-checkpoint checkpoint-handler))) => 120)
        (fact "async sepl miraculously recovers from crash"
              (:state (last (as-seq (async-sepl flows state steps
                                                :with-checkpoint checkpoint-handler)))) => 120)))))
