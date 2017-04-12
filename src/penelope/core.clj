(ns penelope.core
  (:require [datomic.api :as d]
            [clojure.string :as str])
  (:import (datomic.db DbId)
           (java.util List)))

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

(defn attribute
  "Like datomic.api/attribute, except inconsistent values are normalized.
  (E.g. :is-component will always be false on non-refs.)"
  [db attr]
  (let [{:keys [id ident value-type cardinality indexed has-avet unique
                is-component no-history fulltext]} (d/attribute db attr)]
    {:id           id
     :ident        ident
     :value-type   value-type
     :cardinality  cardinality
     :indexed      indexed
     :has-avet     has-avet
     :unique       unique
     :is-component (if (= value-type :db.type/ref) is-component false)
     :no-history   no-history
     :fulltext     fulltext}))

(defn part
  "Return the partition id of an entity id, ident, lookup ref, or tempid
  (string, negative long, and tempid record).

  This is an extended datomic.api/part."
  [db x]
  (cond
    (= x "datomic.tx") (d/entid db :db.part/tx)
    (string? x) (d/entid db :db.part/user)
    (instance? DbId x) (d/entid db (:part x))
    (number? x) (d/part x)
    :else (some-> (d/entid db x) d/part)))

(defn part-ident
  "Return the partition ident of an entity id, ident, lookup ref, or tempid
  (string, negative long, and tempid record).

  Like penelope.core/part, except returns the ident of a partition (if it has
  one) instead of the id."
  [db x]
  (cond
    (= x "datomic.tx") :db.part/tx
    (string? x) :db.part/user
    (instance? DbId x) (d/ident db (:part x))
    (number? x) (d/ident db (d/part x))
    :else (some->> (d/entid db x) d/part (d/ident db))))

(defn schema-map?
  "Return true if this transaction map describes a schema entity, else false."
  [m]
  (and (or (some? (:db/cardinality m)) (some? (:db/valueType m)))
    (or (nil? (:db/id m)) (zero? (:part (:db/id m)))
      (= (:part (:db/id m)) :db.part/db))))

(defn reverse-attr?
  "Return true if given keyword is a reverse-attribute, else false."
  [kw]
  (str/starts-with? (name kw) "_"))

(defn forward-attr
  "Coerce the supplied attribute keyword into a forward attribute. For example,
  :foo/_bar will become :foo/bar."
  [kw]
  (if (reverse-attr? kw)
    (keyword (namespace kw) (subs (name kw) 1))
    kw))

(defn expand-tx-map
  "Expand a map form of a transaction item to its eqivalent :db/add assertions.

  Edge cases of note:

  * List, vector, or set-like cardinality-many attribute values are interpreted
    as multiple values to assert instead of lookup refs.
  * Scalar or map-like cardinality-many attribute values are legal.
  * Submaps of ref-typed is-component attributes which do not contain a :db/id
    will gain a tempid in the same partition as their parent map.
  * The value of a reverse attribute must be a single entity reference,
    regardless of the attribute's cardinality. This restriction is consistent
    with datomic's native map expansion; this function may lift it in the
    future."
  [db txm]
  (let [id (or (:db/id txm) (if (schema-map? txm)
                              (d/tempid :db.part/db)
                              (d/tempid :db.part/user)))]
    (reduce-kv
      (fn rstep [r k v]
        (if (reverse-attr? k)
          (conj r [:db/add v (forward-attr k) id])
          (let [{attr-card  :cardinality
                 component? :is-component} (d/attribute db k)
                effectively-many? (and (= attr-card :db.cardinality/many)
                                    (or (instance? List v) (set? v)))]
            (cond
              effectively-many? (reduce #(rstep %1 k %2) r v)
              (map? v) (into r (expand-tx-map db
                                 (if (and component? (nil? (:db/id v)))
                                   (assoc v :db/id (d/tempid
                                                     (if (schema-map? v)
                                                       :db.part/db
                                                       (part-ident db id))))
                                   v)))
              :else (conj r [:db/add id k v])))))
      [] (dissoc txm :db/id))))
