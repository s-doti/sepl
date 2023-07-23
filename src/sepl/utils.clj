(ns sepl.utils
  (:require [taoensso.timbre :as logger]))

(defn current-time-ms
  "A convenience wrapper around System/currentTimeMillis
   meant to help with time-sensitive tests (easy mocking)."
  [] (System/currentTimeMillis))

;todo deferred steps: add last?
(defn add-steps
  "Add new steps to current steps."
  [new-steps
   steps
   {:keys [stack depth] :as source-step}
   s11n-fn]

  (let [update-stack #(->> {:time     (current-time-ms)
                            :branches (count new-steps)
                            :branch   (- (count new-steps) %)}
                           (merge (dissoc source-step :stack))
                           (conj stack))
        ->tid (juxt :type (comp hash :args))
        new-depth ((fnil inc 0) depth)]
    (->> new-steps
         (map #(update % :args s11n-fn))
         (map #(assoc % :depth new-depth))
         (map-indexed #(assoc %2 :stack (update-stack %1)))
         (map #(assoc % :tid (->tid %)))
         (into steps))))

(defn update-tracker
  "Follow various metrics."
  ([] (update-tracker nil nil))
  ([tracker {:keys [type depth] :as step}]
   (if depth
     (-> tracker
         (update :iterations inc)
         (update :max-depth max depth)
         (update-in [:flows type :iterations] (fnil inc 0))
         (update-in [:flows type :max-depth] (fnil max 0) depth)
         (update-in [:flows type :depths] (fnil conj #{}) depth))
     {:iterations   0
      :step         step
      :flows        {}
      :max-depth    0
      :start-time   (current-time-ms)
      :computations []})))

(defn assert-sepl-computation-thresholds
  "Safe-guard against infinite executions."
  [{:keys [iterations max-depth start-time computations] :as tracker}
   max-iterations
   max-computation-depth
   max-duration]

  (assert (<= iterations max-iterations) tracker)
  (assert (<= max-depth max-computation-depth) tracker)
  (assert (<= (- (current-time-ms) start-time) max-duration) tracker))

(defn- wrap-throwable
  "In case of Error - collect potentially helpful data."
  [step args side-effect? f t]
  (if side-effect?
    (Exception. (format "SEPL failure occurred in step %s (side-effect:%s args:%s)" step f args) t)
    (Exception. (format "SEPL failure occurred in step %s (process:%s, args:%s)" step f args) t)))

(defn verbosely-fail
  "Returns a fn which applies provided f onto args, and returns
   the outcome. In case of Error, throws a wrapped Exception
   with helpful data."
  [step original-args side-effect? f]
  (fn [& args]
    (try (apply f args)
         (catch Throwable t
           (throw (wrap-throwable step
                                  original-args
                                  side-effect?
                                  f
                                  t))))))

(defn- analyze-stack
  "Returns the longest running step in the stack."
  [stack]
  (when (not-empty stack)
    (let [depth (count stack)
          elapsed (- (current-time-ms) (:time (first stack)))
          [step gap] (reduce (fn [[longest-step max-gap time+1] {:keys [time] :as step}]
                               (if (> (- time+1 time) max-gap)
                                 [step (- time+1 time) time]
                                 [longest-step max-gap time]))
                             [nil 0 (current-time-ms)]
                             stack)]
      [step gap])))

(defn report-long-step
  "Log warnings."
  [step more-steps tracker io]

  (try
    (logger/warn (:step tracker)
                 "-LONG_STEP->" (select-keys tracker [:iterations :max-depth #_:flows])
                 "more:" (count more-steps))

    (when-let [[{:keys [type args depth branches branch] :as step} gap]
               (analyze-stack (:stack step))]
      ;ping
      (when io
        (swap! io assoc :ping [step gap]))
      ;log warning
      (let [line "CURRENTLY flow:%s node/attribute:%s depth:%d working on branch %d/%d elapsed:%d eta:%d"]
        (logger/warn (format line type ((juxt :node :attribute) args)
                             depth branch branches gap
                             (int (* (max 1 (- branches branch)) (/ gap (max 1 branch))))))))
    (catch Throwable t
      (logger/error t))))

(defn report-done
  "Log debug."
  [tracker]
  (when-let [init-step (:step tracker)]
    (logger/debug init-step
                  "-DONE->" (select-keys tracker [:iterations :max-depth #_:flows])
                  (- (current-time-ms) (:start-time tracker (current-time-ms))) "ms")))
