(ns sepl.t-sepl
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info
                               ?form))))

;this is a demonstration of how core apis come together

(declare side-effect-fn process-fn)

(defn apis-basic [sepl-fn]
  (fact
    "sepl apis - basic"

    (let [flows {..flow.. {:side-effect-fn side-effect-fn :process-fn process-fn}}
          initial-state ..state..
          step (->step ..flow.. ..args..)]

      (sepl-fn flows initial-state [step])) => ..state..

    (provided
      (side-effect-fn ..state.. ..args..) => ..outcome..
      (process-fn ..args.. ..outcome.. anything) => (or nil []))))

(defn apis-elaborate [sepl-fn]
  (fact
    "sepl apis - more elaborate"

    (let [simple {:side-effect-fn side-effect-fn :process-fn process-fn}
          state-mutating {:side-effect-fn side-effect-fn}]

      (sepl-fn
        {..flow1.. simple                                   ;invokes flows 2 and 3
         ..flow2.. state-mutating                           ;mutating state
         ..flow3.. simple}                                  ;no further steps
        ..state1..
        [(->step ..flow1.. ..args1..)])) => ..state2..

    (provided
      (side-effect-fn ..state1.. ..args1..) => ..outcome1..
      (process-fn ..args1.. ..outcome1.. anything) => (-> '()
                                                          (->step ..flow2.. ..args2..)
                                                          (->step ..flow3.. ..args3..))
      (side-effect-fn ..state1.. ..args2..) => ..state2..
      (side-effect-fn ..state2.. ..args3..) => ..outcome3..
      (process-fn ..args3.. ..outcome3.. anything) => (or nil []))))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp :state last lazy-sepl))
(def async-sepl-fn (comp :state last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "sepl at work"
        (apis-basic sepl-fn))
  (fact "lazy sepl at work"
        (apis-basic lazy-sepl-fn))
  (fact "async sepl at work"
        (apis-basic async-sepl-fn))

  (fact "sepl at work - more elaborate"
        (apis-elaborate sepl-fn))
  (fact "lazy sepl at work - more elaborate"
        (apis-elaborate lazy-sepl-fn))
  (fact "async sepl at work - more elaborate"
        (apis-elaborate async-sepl-fn)))
