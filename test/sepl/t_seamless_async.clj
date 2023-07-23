(ns sepl.t-seamless-async
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [*async?* set-*async?* as-seq]]
            [clojure.core.async :refer [<!! go]]))

(background
  (around :facts
          (let [current *async?*]
            (logger/with-level :info ?form)
            (set-*async?* current))))

;yes, code can be written once, and then run in a blocking or async manner
;regardless of whether side effects that are used underneath are blocking or async

(declare side-effect-fn process-fn)

(defn exec [sepl-fn async-flag async-side-effect? async-retval?]
  (fact
    "sepl apis"

    (set-*async?* async-flag)

    (let [flows {..flow.. {:side-effect-fn side-effect-fn :process-fn process-fn}}
          initial-state ..state..
          step (->step ..flow.. ..args..)]

      (cond-> (sepl-fn flows initial-state [step])
              async-retval? <!!)) => ..state..

    (provided
      (side-effect-fn ..state.. ..args..) => (if async-side-effect? (go ..outcome..) ..outcome..)
      (process-fn ..args.. ..outcome.. anything) => (or nil []))))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp :state last lazy-sepl))
(def async-sepl-fn (comp :state last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "sepl apis while *async?*=false, side effect is blocking"
        (exec sepl-fn false false false))

  (fact "sepl apis while *async?*=false, side effect is async"
        (exec sepl-fn false true false))

  (fact "sepl apis while *async?*=true, side effect is blocking"
        (exec sepl-fn true false true))

  (fact "sepl apis while *async?*=true, side effect is async"
        (exec sepl-fn true true true))

  ;lazy

  (fact "lazy sepl apis while *async?*=false, side effect is blocking"
        (exec lazy-sepl-fn false false false))

  (fact "lazy sepl apis while *async?*=false, side effect is async"
        (exec lazy-sepl-fn false true false))

  (fact "lazy sepl apis while *async?*=true, side effect is blocking"
        (exec lazy-sepl-fn true false false))

  (fact "lazy sepl apis while *async?*=true, side effect is async"
        (exec lazy-sepl-fn true true false))

  ;async

  (fact "async sepl apis while *async?*=false, side effect is blocking"
        (exec async-sepl-fn false false false))

  (fact "async sepl apis while *async?*=false, side effect is async"
        (exec async-sepl-fn false true false))

  (fact "async sepl apis while *async?*=true, side effect is blocking"
        (exec async-sepl-fn true false false))

  (fact "async sepl apis while *async?*=true, side effect is async"
        (exec async-sepl-fn true true false))

  )
