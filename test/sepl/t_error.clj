(ns sepl.t-error
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [*async?* set-*async?* as-seq]]
            [clojure.core.async :refer [<!!]]))

(background
  (around :facts
          (let [current *async?*]
            (logger/with-level :info ?form)
            (set-*async?* current))))

;a little about error handling behavior

(declare side-effect-fn process-fn)

(defn throws-exception [sepl-fn]
  (fact
    "sepl throws exception"

    (let [flows {..flow.. {:side-effect-fn side-effect-fn :process-fn process-fn}}
          initial-state ..state..
          step (->step ..flow.. ..args..)]

      (sepl-fn flows initial-state [step])) => (throws Exception)

    (provided
      (side-effect-fn ..state.. ..args..) =throws=> (Exception. "To be thrown"))))

(defn returns-exception [sepl-fn]
  (fact
    "sepl returns exception"

    (let [flows {..flow.. {:side-effect-fn side-effect-fn :process-fn process-fn}}
          initial-state ..state..
          step (->step ..flow.. ..args..)]

      (type (sepl-fn flows initial-state [step]))) => Exception

    (provided
      (side-effect-fn ..state.. ..args..) =throws=> (Exception. "To be returned"))))

(def sepl-fn sepl)
(def lazy-sepl-fn (comp last lazy-sepl))
(def async-sepl-fn (comp last as-seq async-sepl))

(midje.config/at-print-level
  :print-facts

  (fact "sepl throws exceptions while *async?*=false"
        (set-*async?* false)
        (throws-exception sepl-fn))
  (fact "sepl returns exceptions while *async?*=true"
        (set-*async?* true)
        (returns-exception (comp <!! sepl-fn)))
  (fact "lazy sepl throws exceptions regardless of *async?*"
        (set-*async?* false)
        (throws-exception lazy-sepl-fn)
        (set-*async?* true)
        (throws-exception lazy-sepl-fn))
  (fact "async sepl returns exceptions regardless of *async?*"
        (set-*async?* false)
        (returns-exception async-sepl-fn)
        (set-*async?* true)
        (returns-exception async-sepl-fn))
)
