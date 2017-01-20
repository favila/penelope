# Penelope

> So every day I’d weave at the big loom. \
> But at night, once the torches were set up, \
> I’d unravel it. \
> <cite>[_The Odyssey_, Book IX][1]</cite>

**Penelope** is a Clojure utility library for preparing, transforming,
and submitting transactions to Datomic.

Planned features:

* Flatten transaction map forms to datoms.
* Get a list of datoms for a pull expression.
* Complete transactions on a peer speculatively, check-and-set/abort/retry if
  an invariant is violated when the transactor attempts to apply it.
* Automatically split large transactions up intelligently (e.g. during a
  bulk import).
* Standard transaction metadata: tx saga, unexpanded tx, peer identifier,
  patch identifier
* Patches: pre+post condition invariants, splittable to mutiple txs,
  unique identifier, can be speculatively transacted.
* Transaction pipeliner.
* Undo a transaction.
* Reset values of a cardinality-many attribute
* Unique component entities.
* Sibling order attribute.
* History-collapsing: produce the minimum number of assertions (preserving
  tx history for those assertions) to recreate a non-history-database value.
  (I.e., elide retractions and assertions which were later retracted.)

## License

Copyright © 2017 Francis Avila

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

[1]: http://records.viu.ca/~johnstoi/homer/odyssey19.htm
