(ns penelope.core
  (:require [datomic.api :as d]))

(defn expand-schema-vec
  "Given a schema vector, return a normal schema transaction map.

  A Schema vector is a more compact syntax for attribute schema. Example:

      (expand-schema-vec ['foo/bar :one :ref :component [:custom :value]])
      ;=>
      {:db/ident :foo/bar,
       :db/cardinality :db.cardinality/one,
       :db/valueType :db.type/ref,
       :db/isComponent true,
       :custom :value}

  The following items can appear in a schema vector in any order:

  * A string in the vector becomes the :db/doc value.
  * A symbol becomes the :db/ident keyword.
  * :one or :many set cardinality one or many.
  * :keyword, :string, :boolean, :ref, etc set value type.
  * :unique-value sets :db.unique/value.
  * :identity sets :db.unique/identity.
  * :index indexes the attribute.
  * :fulltext adds a fulltext index.
  * :component sets :db/isComponent true.
  * :no-history sets :db/noHistory true.
  * Vectors of [key value] are added to the map as-is.

  This transformation from vector to map is purely syntatic: no validation is
  done to ensure the expressed schema is valid."
  [schema-vec]
  (reduce
    (fn [acc item]
      (cond
        (string? item) (assoc acc :db/doc item)
        (symbol? item) (assoc acc :db/ident (keyword (namespace item)
                                              (name item)))
        (vector? item) (assoc acc (first item) (second item))
        (keyword? item)
        (case item
          :one (assoc acc :db/cardinality :db.cardinality/one)
          :many (assoc acc :db/cardinality :db.cardinality/many)
          (:keyword :string :boolean :long :bigint :float :double :bigdec :ref
            :instant :uuid :uri :bytes)
          (assoc acc :db/valueType (keyword "db.type" (name item)))

          :unique-value (assoc acc :db/index true
                                   :db/unique :db.unique/value)
          :identity (assoc acc :db/index true
                               :db/unique :db.unique/identity)

          :index (assoc acc :db/index true)
          :fulltext (assoc acc :db/fulltext true)
          :component (assoc acc :db/isComponent true)
          :no-history (assoc acc :db/noHistory true))))
    {} schema-vec))
