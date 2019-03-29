(ns datest.core
  (:require [datomic.api :as d]
            [clojure.pprint :as pp]))

(def uri "datomic:free://localhost:4334/todo")

;; Remove old database while we're testing
(d/delete-database uri)

;; Create the database
(d/create-database uri)

;; Connect to the database
(def conn (d/connect uri))

;; Beware! setting up a db connection gives you a reference to a datomic database "at a point in time"
;; If you update the database you will need to update this reference. Better to use (db/d conn) when submitting
;; a query so that you're getting the most recent view of the database.
(def db (d/db conn))

;; Read the sample schema
(def scheme-tx (read-string (slurp "/Users/zand/dev/datest/src/datest/todo_schema.edn")))

;; Test that we have schema by displaying the first element
(first scheme-tx)

;; submit schema transaction
@(d/transact conn datest.core/scheme-tx)

;; read the sample data
(def data-tx (read-string (slurp "/Users/zand/dev/datest/src/datest/tododata.edn")))

;; Test that we have schema by displaying the first element
(first data-tx)

@(d/transact conn data-tx)

;; Basic Query
;; What's the value of the :db/index attribute?
(d/q '[:find ?v
       :where [?e :db/index ?v]]
     (d/db conn))

;; use a pull expression to show me everything about the :db/index attribute
(d/pull db '[*] :db/index)


;; Find all lists
(def all-lists-q
  '[:find ?list-name
    :where [_ :list/name ?list-name]])

(d/q all-lists-q (d/db conn))

;; Find the entity whose name is "Groceries"
(d/q '[:find ?e
       :in $ ?list
       :where [?e :list/name ?list]]
     (d/db conn)
     "Groceries")

;; Find all entities who have an attribute of type :list/name
;; aka find all the lists
(d/q '[:find ?e
       :where [?e :list/name _]]
     (d/db conn))

;; Like the above but now return the name of the entity
(d/q '[:find [(pull ?e [:db/id :list/name]) ...]
       :where [?e :list/name]]
     (d/db conn))

;; Like the above but return the todos associated with the lists
(d/q '[:find [(pull ?e [:list/name
                        {:list/todos
                         [:db/id :todo/title :todo/status]}]
                    ) ... ]
       :where [?e :list/name]]
     (d/db conn))

;; Like the above but return the todos associated with the lists
;; Note that because :todo/status is an enum type we need to use nesting and [:db/ident]
(d/q '[:find [(pull ?e [:list/name
                        {:list/todos
                         [:db/id :todo/title {:todo/status [:db/ident]}]}]
                    )]
       :where [?e :list/name]]
     (d/db conn))

(pp/pprint *1)

;; Since there is only one result, omitting "..." makes no difference in this case:
(d/q '[:find [(pull ?e [:list/name :list/todos])]
       :where [?e :list/name]]
     (d/db conn))


;; Basic Transactions

;; To be tested
(def newlist [{:db/id (d/tempid :db.part/user)
               :list/name "Work"
               :list/todos []}])

@(d/transact conn newlist)

;; Find the single entity who's :list/name is "Work"
(def worklist (d/q '[:find ?e .
                     :where [?e :list/name "Work"]]
                   (d/db conn)))

;; Query the DB for the list named Work
(d/q '[:find ?e .
       :where [?e :list/name "Work"]]
     (d/db conn))

;; Show me everything about the entity named worklist
(d/pull (d/db conn) '[*] worklist)

;; Construct a new todo for the worklist entity
(def worknewtodos [
                   {:db/id      worklist
                    :list/todos [
                                 {:db/id (d/tempid :db.part/user)
                                  :todo/title "Project 1"
                                  :todo/note "Note for Project 1"
                                  :todo/status :todo.status/not_started}
                                 ]}])

;; Transact the new todo into the database.
@(d/transact conn worknewtodos)

;; Recursively find all todos in the list named "Work"
(d/q '[:find [(pull ?e
                    [:list/name
                     {:list/todos [:todo/title :todo/note {:todo/status [:db/ident]}]}]) ... ]
       :where [?e :list/name "Work"]] (d/db conn))

;; Construct a new todo for the worklist entity
(def worknewtodos2 [
                   {:db/id      worklist
                    :list/todos [
                                 {:db/id (d/tempid :db.part/user)
                                  :todo/title "Project 2"
                                  :todo/note "Note for Project 2"
                                  :todo/status :todo.status/not_started}
                                 ]}])

;; Transact the new todo into the database.
@(d/transact conn worknewtodos2)

;; find the entity id for the project2 Todo
(def project2
  (d/q '[:find ?e .
          :where [?e :todo/title "Project 2"]] (d/db conn)))

(def subtodo [
              {:db/id project2
               :todo/children [
                               {:db/id (d/tempid :db.part/user)
                                :todo/title "Project 2 Sub Todo 1"
                                :todo/note "Note for P2 ST1"
                                :todo/status :todo.status/not_started}]}])

;; Pull syntax format:
;; (d/pull database pattern entity-id)

;; find everything about the entity 17592186045432 (project 2 todo)
(d/pull (d/db conn) '[*] 17592186045432)

(d/pull (d/db conn) '[:db/id :todo/title {:todo/children ...}] 17592186045432)

;; Add a new sub todo to Project 2
(def subtodop2t2 [
              {:db/id project2
               :todo/children [
                               {:db/id (d/tempid :db.part/user)
                                :todo/title "Project 2 Sub Todo 2"
                                :todo/note "Note for P2 ST2"
                                :todo/status :todo.status/not_started}]}])

;; Transact it to the db
@(d/transact conn subtodop2t2)

;; Get the new value of the db
(def db (d/db conn))

;; Add a sub-todo to Project 2 Sub todo 2 (17592186045436)
(def subtodo221 [
                 {:db/id 17592186045436
                  :todo/children [
                                  {:db/id (d/tempid :db.part/user)
                                   :todo/title "Project 2 Sub Todo 2 Sub Todo 1"
                                   :todo/note "Note for P2 ST2 ST1"
                                   :todo/status :todo.status/not_started}]}
                 ])

;; Transact it to the db
@(d/transact conn subtodo221)

;; Get the new value of the db
(def db (d/db conn))

;; Pull the new values to inspect them
(d/pull (d/db conn) '[:db/id :todo/title {:todo/children ...}] 17592186045432)

;; Use pull to traverse the graph from the Work list through recursion:
;; Not yet working as expected... only giving the :list/name
;; Is this because :list/todos needs to be a Component?
(d/pull (d/db conn) '[:list/name {:list/todos ...}] 17592186045428)

(d/q '[:find [(pull ?e [:db/id
                        :list/name
                        {:list/todos ...}])]
       :where [?e :list/name "Work"]]
     (d/db conn))

@(d/transact conn subtodo)

;; Examples using the Seattle Sample Database

;; Create a new database
(def uri-seattle "datomic:dev://localhost:4334/seattle")

;; Create database
(d/create-database uri-seattle)

;; Create a connection to the new database
(def conn-sea (d/connect uri-seattle))


;; parse schema file
(def schema-sea (read-string (slurp "/Users/zand/bin/datomic-pro-0.9.5394/samples/seattle/seattle-schema.edn")))

;; display first statement
(first schema-sea)

;; submit schema transaction
@(d/transact conn-sea schema-sea)

;; parse seed data edn file
(def data-tx-sea (read-string (slurp "/Users/zand/bin/datomic-pro-0.9.5394/samples/seattle/seattle-data0.edn")))

;; display first three statements in seed data transaction
(first data-tx-sea)
(second data-tx-sea)
(nth data-tx-sea 2)

;; submit seed data transaction
@(d/transact conn-sea data-tx-sea)

;; Entity Maps - concise assertions about entities
;; entity maps can take collections.
