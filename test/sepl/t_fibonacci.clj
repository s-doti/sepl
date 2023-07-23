(ns sepl.t-fibonacci
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;a fibonacci calculator, cause why not?

(midje.config/at-print-level
  :print-facts

  (fact
    "sepl calculates the fibonacci sequence up to position N"

    ;state is the unfolding fibonacci sequence (imagine this is stored in 'the cloud')
    ;the side-effect-fn of 'calc-next' is retrieving the last two elements of the sequence
    ;the process-fn of 'calc-next' is summing them up
    ;the 'set-next' flow is mutating the sequence

    (let [flows {"calc-next" {:side-effect-fn (fn get-last-two [state args]
                                                (take-last 2 state))
                              :process-fn     (fn calc-next [args outcome & [metacontext]]
                                                (when (pos? args)
                                                  (-> '()
                                                      (->step "set-next" (apply + outcome))
                                                      (->step "calc-next" (dec args)))))}
                 "set-next"  {:side-effect-fn (fn mutate-state [state args]
                                                (conj state args))}}
          initial-state [0 1]
          step (->step "calc-next" 7)]

      (fact "sepl works"
            (sepl flows initial-state [step]) => [0 1 1 2 3 5 8 13 21])

      (fact "lazy sepl works"
            (:state (last (lazy-sepl flows initial-state [step]))) => [0 1 1 2 3 5 8 13 21])

      (fact "async sepl works"
            (:state (last (as-seq (async-sepl flows initial-state [step])))) => [0 1 1 2 3 5 8 13 21]))))
