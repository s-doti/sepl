# sepl

Algorithm execution engine.

## Premise

The Side-effect-Process-Loop, or SePL in short, is a simple-yet-powerful algorithm
execution framework.  
It requires the user, you, to express logic to be executed via simple, concise, 
declarative building blocks. In return, the very execution of the algorithm gets 
converted into data, literally. Since data is easy to control and manipulate, so 
too is the execution of the algorithm itself.  
Pragmatically, processes which require elaborate integration with the world outside, 
or which are long-running (and so are naturally more fragile), stand to gain the most 
from using this library.

## Features

*Easy maintenance*<br>
Plug and play with existing or new integrations effortlessly.

*Execution-style agnostic*<br> 
Run async or blocking, locally or remotely, or fuse different styles together.

** The above two are largely thanks to the 
[seamless-async](https://github.com/s-doti/seamless-async) library

*Space-time agnostic*<br>
Execution is data, and data can be sent anywhere (ok, anywhere but backwards in
time, which is doable, yet can be tricky).

*Pause/resume*<br>
Execution is data, and data can be persisted.

*Disaster recovery*<br>
See above.

*Advanced safety measures*<br>
Automatically break execution by the amount of steps taken, overall running 
time, or by depth of the unfolding algorithm computation tree.

*User optimizations*<br>
The user may interject optimization logic, that's capable of mutating the execution 
running state, by observing the unfolding algorithm computation tree.

*Debugging*<br>
Similar to code debugging in concept, but at the algorithm terminology level - 
you debug your own business logic, rather than code lines.

## Usage

This approach may be applied to existing code, or while designing new code. There 
is no need to structure your code differently. The approach does generally encourage 
'clean' code, so for example, code that already maintains proper separation of 
concerns (e.g. side effects divorced from pure business logic) would fit more 
naturally with this.

To use it, SePL requires 3 inputs:

1. <b>Logic declaration</b> - a collection of logic segments called *flows*.  
A *flow* expresses a segment of your logic, which starts with some interaction with 
the world, and moves on to further computations based on the outcome of that 
interaction. Such interactions are called *side effects*, and they're used to either 
enrich the internal state of the execution, or to mutate the external state of the 
world. The computation part of the *flow* is generically called *process*, the 
outcome of which are followup *steps* (see *step* below) to be taken next.   
*Flows* aim to uniquely identify naturally fragile points of execution, 
or to segment your logic to the most high-level building blocks. For example, Ginfer - 
the graph inference library that's built ontop of SePL, only consists of 3 *flows*: 
update, notify, and eval.

2. <b>State</b> - a user-defined, stateful context, to be preserved/mutated throughout the 
execution.  
This state could be anything, it is simply passed as argument to user logic throughout 
the execution. It can be mutated by declaring a *flow* which only has its *side 
effect* declared but lacks *process* - the outcome of the *side effect* would 
effectively become the mutated state for subsequent execution *steps*.

3. <b>Initial step</b> - data model referencing some *flow*, and holding the input arguments 
needed for the execution of that *flow*.  
The execution of *flows* creates further *steps*, so this is simply how the SePL is 
triggered initially. *Steps* serve as the 'glue' between *flows*, and when put together 
in a sequence, they express the algorithm execution as computations tree, in pure data terms.

Let's see what all this means through an example.  
We will show how SePL may be used to execute the Towers of Hanoi solution.
```clojure
user=> (def towers-of-hanoi {"peg-A" [3 2 1]
  #_=>                       "peg-B" []
  #_=>                       "peg-C" []})
#'user/towers-of-hanoi

user=> (def num-disks (count (get towers-of-hanoi "peg-A")))
#'user/num-disks

user=> (defn move-disk
  #_=>   "Move top-most disk from the 'from' peg to the 'to' peg."
  #_=>   [state [from to]]
  #_=>   (-> state
  #_=>       (update from pop)
  #_=>       (update to conj (peek (get state from)))))
#'user/move-disk

user=> (defn get-auxiliary-peg
  #_=>   "Given current state and from/to pegs, identify and return auxiliary peg."
  #_=>   [state [from to]]
  #_=>   (first (keys (dissoc state from to))))
#'user/get-auxiliary-peg
```
So above, we're given the initial state of the problem to solve, and we also 
have some fns which allow us to interact with that state.  
So far, nothing out of the ordinary, this is just exposition - non Sepl yet.

Next, enter SePL.  
We would require 2 *flows*:  
- 'move-disk' would be given from/to pegs as arguments, and it would do just that, with 
the help of the `move-disk` side effect fn. This would be a state mutating *flow* so it 
would have no further computations declared.
- 'solve' would be given the same arguments. For side effect, it would first realize which peg 
can be used as auxiliary. Then it would call itself to move all disks, but the bottom one, 
to the auxiliary peg. Next it would call 'move-disk' to move the single remaining disk to its 
final resting place. Finally, it would call itself once again to move all disks from the 
auxiliary peg, on top of the single disk.  

We define the `solve` fn to carry out all these computations of the 'solve' *flow*.  
To indicate which *flows* should be next, the `->step` api is used, with *flow* id and arguments.

After all that, we're ready to properly declare our `flows` as a mapping from *flow* ids to our logic.

```clojure
user=> (require '[sepl.core :refer [->step sepl lazy-sepl]])
nil

user=> (defn solve
  #_=>   "Move all but bottom disk to auxiliary peg, then move it to target peg,"
  #_=>   "and finally move all disks from auxiliary peg to target peg."
  #_=>   [[from to num-disks]                                      ;args
  #_=>    aux                                                      ;side-effect outcome
  #_=>    & [metacontext]]                                         ;ignore -- not in use
  #_=>   (if (= 1 num-disks)
  #_=>     [(->step "move-disk" [from to])]
  #_=>     (-> '()
  #_=>         (->step "solve" [from aux (- num-disks 1)])
  #_=>         (->step "move-disk" [from to])
  #_=>         (->step "solve" [aux to (- num-disks 1)]))))
#'user/solve

user=> (def flows {"move-disk" {:side-effect-fn move-disk}
  #_=>             "solve"     {:side-effect-fn get-auxiliary-peg
  #_=>                          :process-fn     solve}})
#'user/flows
```

So -  
Our *flows* are properly defined above.  
Our *state* is the very exposition of the Towers of Hanoi problem above.  
For *initial step* we trigger 'solve' to move all disks from peg-A to peg-B.  
Calling the `sepl` api with these would eagerly solve the Towers of Hanoi:

```clojure
user=> (def initial-step (->step "solve" ["peg-A" "peg-B" num-disks]))
#'user/initial-step

user=> (sepl flows towers-of-hanoi [initial-step])
{"peg-A" [], "peg-B" [3 2 1], "peg-C" []}
```

Many words, very little code. Simple?

Lets have a look at a slightly different execution api.  
`lazy-sepl` is triggered in much the same fashion, but returns a lazy sequence of execution
*steps*. *Steps* are being executed as they're being pulled from the sequence. The following 
specifically filters those state-mutating 'move-disk' *steps*, and gives us the state at those 
positions:

```clojure
user=> (->> (lazy-sepl flows towers-of-hanoi [initial-step])
  #_=>      (filter (comp #{"move-disk"} :type :step))
  #_=>      (map :state))
[{"peg-A" [3 2] "peg-B" [1] "peg-C" []}
 {"peg-A" [3] "peg-B" [1] "peg-C" [2]}
 {"peg-A" [3] "peg-B" [] "peg-C" [2 1]}
 {"peg-A" [] "peg-B" [3] "peg-C" [2 1]}
 {"peg-A" [1] "peg-B" [3] "peg-C" [2]}
 {"peg-A" [1] "peg-B" [3 2] "peg-C" []}
 {"peg-A" [] "peg-B" [3 2 1] "peg-C" []}]
```

This is cool!  
It gives a sense of what the logic was 'thinking' at each *step* of the way.

It is important to understand - where 'state' is used in code above, it is really an analogue for 
db/cloud/remote storage access; and the Towers of Hanoi is logic analogue of some long-running 
computation, hiding dozens of fns, and hundreds of code lines, under a *flow* construct. 
Framing these in SePL terms did not require us to do anything much different than we normally would, 
it simply gave us an unparalleled level of control over the execution of the logic.

How?<br>
SePL is merely a simple iteration over data maps, and nothing more.  
Each of those maps, a *step*, is referencing a single building-block of our logic (a *flow*), and 
carrying the arguments for its invocation.  
During each iteration, the *flow*'s side effect is executed 
over the global state and using given arguments. The outcome is then passed along with those same 
arguments as input for the *flow*'s *process*, to determine next *steps*. And so the loop keeps 
feeding itself, until all *steps* have exhausted. *Steps* are added at the head, which gives a DFS 
style execution, rather than at the tail (which would have produced a BFS walk).
When all is set and done, the sequence of *steps* that was produced, is essentially an expression 
of the execution computations tree that was unfolded. This is how SePL is successfully converting 
execution of logic into data.

If all this is a bit much, but you are curious, perhaps the 
[baby-sepl](https://github.com/s-doti/baby-sepl) library is just what you need. It 
demonstrates the essence of the concept expressed here, but does so as a gist, in less
than 20 lines of code..

It is worth mentioning one more execution variation - `async-sepl` is somewhat similar 
to `lazy-sepl`; it pushes interim states onto a channel, out of which they are pulled 
one by one. All SePL execution variations in essence do exactly the same thing, these 
are just different implementation flavours.

Further examples are expressed as tests:
- A demonstration of how [core APIs](test/sepl/t_sepl.clj) come together
- Written-once, execution-agnostic - [seeing](test/sepl/t_seamless_async.clj) is believing
- [Serialize args](test/sepl/t_s11n.clj), so that internal execution state could 
be sent elsewhere or persisted
- DR [demonstration](test/sepl/t_checkpoint.clj) (pause/resume would work just as well)
- See how execution [thresholds](test/sepl/t_threshold.clj) can be defined
- Advanced user [optimization api](test/sepl/t_optimizer.clj) can be applied to the 
  unfolding computation tree
- A [debugger](test/sepl/t_debugger.clj) gist
- A little about [error handling](test/sepl/t_error.clj) behavior
- A Fibonacci [calculator](test/sepl/t_fibonacci.clj), cause why not?
- The Towers of Hanoi [problem](test/sepl/t_towers_of_hanoi.clj)

## License

Copyright © 2023 [@s-doti](https://github.com/s-doti)

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
