(ns sepl.t-towers-of-hanoi
  (:require [midje.sweet :refer :all]
            [taoensso.timbre :as logger]
            [sepl.core :refer :all]
            [seamless-async.core :refer [as-seq]]))

(background
  (around :facts
          (let []
            (logger/with-level :info ?form))))

;the towers of hanoi problem

(def towers-of-hanoi {"peg-A" [3 2 1]
                      "peg-B" []
                      "peg-C" []})

(def num-disks (count (get towers-of-hanoi "peg-A")))

(defn move-disk
  "Move top-most disk from the 'from' peg to the 'to' peg."
  [state [from to]]
  (-> state
      (update from pop)
      (update to conj (peek (get state from)))))

(defn get-auxiliary-peg
  "Given current state and from/to pegs, identify and return auxiliary peg."
  [state [from to]]
  (first (keys (dissoc state from to))))

(defn solve
  "Move all but bottom disk to auxiliary peg, then move it to target peg,
   and finally move all disks from auxiliary peg to target peg."
  [[from to num-disks]                                      ;args
   aux                                                      ;side-effect outcome
   & [metacontext]]                                         ;ignore -- not in use
  (if (= 1 num-disks)
    [(->step "move-disk" [from to])]
    (-> '()
        (->step "solve" [from aux (- num-disks 1)])
        (->step "move-disk" [from to])
        (->step "solve" [aux to (- num-disks 1)]))))

(def flows {"move-disk" {:side-effect-fn move-disk}
            "solve"     {:side-effect-fn get-auxiliary-peg
                         :process-fn     solve}})

(def initial-step (->step "solve" ["peg-A" "peg-B" num-disks]))

(midje.config/at-print-level
  :print-facts

  (fact
    "sepl solves the towers of hanoi for N disks"

    (let []

      (fact "sepl works"
            (sepl flows
                  towers-of-hanoi
                  [initial-step]) => {"peg-A" []
                                      "peg-B" [3 2 1]
                                      "peg-C" []})
      (fact "lazy sepl works"
            (->> (lazy-sepl flows towers-of-hanoi [initial-step])
                 (filter (comp #{"move-disk"} :type :step))
                 (map :state)) => [{"peg-A" [3 2] "peg-B" [1] "peg-C" []}
                                   {"peg-A" [3] "peg-B" [1] "peg-C" [2]}
                                   {"peg-A" [3] "peg-B" [] "peg-C" [2 1]}
                                   {"peg-A" [] "peg-B" [3] "peg-C" [2 1]}
                                   {"peg-A" [1] "peg-B" [3] "peg-C" [2]}
                                   {"peg-A" [1] "peg-B" [3 2] "peg-C" []}
                                   {"peg-A" [] "peg-B" [3 2 1] "peg-C" []}])
      (fact "async sepl works"
            (->> (async-sepl flows
                             towers-of-hanoi
                             [initial-step])
                 (as-seq)
                 (filter (comp #{"move-disk"} :type :step))
                 (map :state)) => [{"peg-A" [3 2] "peg-B" [1] "peg-C" []}
                                   {"peg-A" [3] "peg-B" [1] "peg-C" [2]}
                                   {"peg-A" [3] "peg-B" [] "peg-C" [2 1]}
                                   {"peg-A" [] "peg-B" [3] "peg-C" [2 1]}
                                   {"peg-A" [1] "peg-B" [3] "peg-C" [2]}
                                   {"peg-A" [1] "peg-B" [3 2] "peg-C" []}
                                   {"peg-A" [] "peg-B" [3 2 1] "peg-C" []}]))))
