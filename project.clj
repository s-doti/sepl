(defproject com.github.s-doti/sepl "1.0.1"
  :description "Algorithm execution engine"
  :url "https://github.com/s-doti/sepl"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.taoensso/timbre "6.2.1"]
                 [com.github.s-doti/seamless-async "1.0.2"]]
  :profiles {:dev {:dependencies [[midje "1.10.9"]]}}
  :repl-options {:init-ns sepl.core})
