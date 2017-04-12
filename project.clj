(defproject penelope "0.1.0-SNAPSHOT"
  :description "Datomic transaction toolbox"
  :url "https://github.com/favila/penelope"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:provided
             {:dependencies [[com.datomic/datomic-free "0.9.5561"]]}})
