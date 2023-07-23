(ns sepl.t-s11n
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;serialize args, so that internal execution state could
;be sent elsewhere or persisted

(declare side-effect-fn process-fn s11n-args de-s11n-args)

(defn exec [sepl-fn]
  (fact
    "args s11n"

    (sepl-fn
      {..flow1.. {:side-effect-fn side-effect-fn :process-fn process-fn}
       ..flow2.. {:side-effect-fn side-effect-fn}}
      ..state1..
      [(->step ..flow1.. ..s11n-args1..)]
      :->ser-args s11n-args
      :->deser-args de-s11n-args) => ..state2..

    (provided
      (de-s11n-args ..s11n-args1..) => ..args1..
      (side-effect-fn ..state1.. ..args1..) => ..outcome..
      (process-fn ..args1.. ..outcome.. anything) => [(->step ..flow2.. ..args2..)]
      (s11n-args ..args2..) => ..s11n-args2..
      (de-s11n-args ..s11n-args2..) => ..args2..
      (side-effect-fn ..state1.. ..args2..) => ..state2..)))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp :state last lazy-sepl))
(def async-sepl-fn (comp :state last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "sepl works" (exec sepl-fn))
  (fact "lazy sepl works" (exec lazy-sepl-fn))
  (fact "async sepl works" (exec async-sepl-fn)))
