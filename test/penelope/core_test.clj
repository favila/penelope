(ns penelope.core-test
  (:require [clojure.test :refer [deftest is]]
            [penelope.core :as p]
            [datomic.api :as d]))

(deftest expand-schema-vec
  (is (= (p/expand-schema-vec ['foo/bar :one :ref :component [:custom :value]])
        {:db/ident       :foo/bar,
         :db/cardinality :db.cardinality/one,
         :db/valueType   :db.type/ref,
         :db/isComponent true,
         :custom         :value})))
