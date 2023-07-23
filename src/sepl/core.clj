(ns sepl.core
  (:require [sepl.sepl :as sepl]
            [sepl.lazy-steps :as lazy]
            [sepl.async-steps :as async]))

(defn ->step
  "Returns a 'step' map with entries per its type and args.
   If steps are provided, adds the new step and returns the updated sequence."

  ([type args]
   {:type type :args args})

  ([steps type args]
   (conj steps (->step type args))))

(def sepl
  "The side-effect-process-loop is essentially an iteration over a sequence
   of data maps.
   Each such map - aka step, aka event, aka trigger - contains a ref to
   descriptive logic and args to invoke it with.
   A state is being preserved throughout this process, similar to a reduce
   operation in essence.
   During each iteration, declared side effect logic is executed with the
   state and args in context. The side effect outcome is then passed along
   with the same args to declared pure logic, which generates subsequent
   steps to execute next. Where no pure logic is declared, the side
   effect outcome becomes the new state in followup iterations.

   This code is built with seamless-async semantics, so it may be executed
   in a blocking or async manner just the same, regardless of side effects
   behavior.

   In addition, in between iterations, steps are optionally serialized, to
   support non-local execution. This in turns allows for advanced features
   such as DR scenarios, distributed algorithm execution, etc.

   The user may also provide specific checkpointing logic, which expects
   to get the Sepl internal runtime state (essentially steps) and should
   persist this data aside. In case of need, this state can be later read
   and provided as steps, to commence computations from that point.

   For safety measures, iterations, and total running time, may be capped,
   as well as the depth of the computation tree which unfolds whilst an
   algorithm is being executed.

   Where execution may be optimized by looking ahead at future steps as a
   batch, user-specific optimization logic is supported and will be applied
   per each iteration.

   Finally, this utility comes with debug api which allows for the usual
   debug operations we all know and love(?)."
  sepl/sepl)

(def lazy-sepl
  "Same as calling sepl, but instead of eagerly computing final
   state, returns a lazy sequence of each interim state. The
   algorithm gets lazily executed by iterating the returned
   sequence.

   The interim state is a map having:
   - The current step being executed.
   - All visible future steps in the remaining computation tree yet to be unfolded.
   - The mutating state context.
   - Tracker; a collection of various metrics.
   - The current step's side effect outcome."
  lazy/sepl)

(def async-sepl
  "Same as calling sepl, but instead of eagerly computing final
   state, returns a channel from which the next interim state can
   be retrieved. The channel closes when the final state is
   pulled from it. The algorithm gets 'lazily' executed as interim
   states are taken from the channel.

   The interim state is a map having:
   - The current step being executed.
   - All visible future steps in the remaining computation tree yet to be unfolded.
   - The mutating state context.
   - Tracker; a collection of various metrics.
   - The current step's side effect outcome.

   It is possible to configure how many steps are eagerly computed
   ahead of time via the aot optional arg (default:1)."
  async/sepl)
