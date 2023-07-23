(ns sepl.t-optimizer
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;advanced user optimization api can be applied to the unfolding computation tree

(declare side-effect-fn process-fn optimizer-fn)

(defn exec [sepl-fn]
  (fact
    "optimizer"

    (let [flows {..flow.. {:side-effect-fn side-effect-fn :process-fn process-fn}}
          initial-state ..state..
          step (->step ..flow.. ..args..)]

      (sepl-fn flows initial-state [step]
               :with-optimizer optimizer-fn)) => ..optimized-state..

    (provided
      (optimizer-fn ..state.. [..args..]) => ..optimized-state..
      (side-effect-fn ..optimized-state.. ..args..) => ..outcome..
      (process-fn ..args.. ..outcome.. anything) => (or nil []))))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp :state last lazy-sepl))
(def async-sepl-fn (comp :state last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "sepl works" (exec sepl-fn))
  (fact "lazy sepl works" (exec lazy-sepl-fn))
  (fact "async sepl works" (exec async-sepl-fn)))
