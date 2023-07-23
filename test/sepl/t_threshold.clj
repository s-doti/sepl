(ns sepl.t-threshold
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;this is how execution thresholds can be defined

(defn exec [sepl-fn arg threshold]
  (sepl-fn
    {"inf" {:side-effect-fn (constantly nil)
            :process-fn     (constantly [(->step "inf" nil)])}}
    nil
    [(->step "inf" nil)]
    arg threshold))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp :state last lazy-sepl))
(def async-sepl-fn (comp #(.getMessage %) last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "max iterations"
        (exec sepl-fn :max-iterations 10) => (throws AssertionError
                                                     #"iterations 11"
                                                     #"step \{:type \"inf\", :args nil\}"
                                                     #"\(<= iterations max-iterations\)"))
  (fact "lazy max iterations"
        (exec lazy-sepl-fn :max-iterations 10) => (throws AssertionError
                                                          #"iterations 11"
                                                          #"step \{:type \"inf\", :args nil\}"
                                                          #"\(<= iterations max-iterations\)"))
  (fact "async max iterations"
        (exec async-sepl-fn :max-iterations 10) => (every-checker
                                                     (contains "iterations 11")
                                                     (contains "step {:type \"inf\", :args nil}")
                                                     (contains "(<= iterations max-iterations)")))
  (fact "max duration"
        (exec sepl-fn :max-duration 0) => (throws AssertionError
                                                  ;#"iterations 494"..
                                                  #"step \{:type \"inf\", :args nil\}"
                                                  #"\(<= \(- \(current-time-ms\) start-time\) max-duration\)"))

  (fact "lazy max duration"
        (exec lazy-sepl-fn :max-duration 0) => (throws AssertionError
                                                       ;#"iterations 494"..
                                                       #"step \{:type \"inf\", :args nil\}"
                                                       #"\(<= \(- \(current-time-ms\) start-time\) max-duration\)"))
  (fact "async max duration"
        (exec async-sepl-fn :max-duration 0) => (every-checker
                                                  ;(contains "iterations 494")
                                                  (contains "step {:type \"inf\", :args nil}")
                                                  (contains "(<= (- (current-time-ms) start-time) max-duration)")))
  (fact "max depth"
        (exec sepl-fn :max-depth 10) => (throws AssertionError
                                                #"max-depth 11"
                                                #"step \{:type \"inf\", :args nil\}"
                                                #"(<= max-depth max-computation-depth)"))
  (fact "lazy max depth"
        (exec lazy-sepl-fn :max-depth 10) => (throws AssertionError
                                                     #"max-depth 11"
                                                     #"step \{:type \"inf\", :args nil\}"
                                                     #"\(<= max-depth max-computation-depth\)"))
  (fact "async max depth"
        (exec async-sepl-fn :max-depth 10) => (every-checker
                                                (contains "max-depth 11")
                                                (contains "step {:type \"inf\", :args nil}")
                                                (contains "(<= max-depth max-computation-depth)"))))
