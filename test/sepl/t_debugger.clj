(ns sepl.t-debugger
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [clojure.core.async :refer [go timeout <!]]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;a debugger gist

(defn exec [sepl-fn]
  (fact
    "debugger"

    (let [flows {"flow" {:side-effect-fn (constantly nil) :process-fn (constantly nil)}}
          initial-state nil
          step (->step "flow" nil)
          io (atom {:breakpoint {:type "flow"}              ;break on 'flow' (specific args also supported)
                    :resume (promise)})                     ;expect :step-into/over/out or else play to end
          debug-info-entries '(:step :side-effect-outcome :child-steps :visible-branches)
          get-debug-info-collected #(keys (dissoc @io :breakpoint :resume))]

      (go
        ;execution will reach the breakpoint.. eventually..
        (while (= 2 (count @io))
          (<! (timeout 1)))
        ;verify debug info collected
        (fact "debug info"
              (get-debug-info-collected) => debug-info-entries)
        ;release debugger to commence execution
        ;could use :step-into/over/out here, but we want to run to end
        (swap! io update :resume deliver :run-to-end))

      ;the following would break per declared breakpoint inside io
      (sepl-fn flows initial-state [step]
               :io io)

      ;verify debug info collected, ie execution actually stopped at the breakpoint
      (get-debug-info-collected) => debug-info-entries)))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp :state last lazy-sepl))
(def async-sepl-fn (comp :state last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "sepl works" (exec sepl-fn))
  (fact "lazy sepl works" (exec lazy-sepl-fn))
  (fact "async sepl works" (exec async-sepl-fn)))
