(ns data-dive.db
  (:require [monger.collection :as col]
            [monger.core :as m]
            [monger.credentials :as mcr]
            [monger.db :as mdb]
            [monger.joda-time]
            [monger.operators :refer :all]
            [monger.query :as q]
            [monger.result :as res]
            [taoensso.timbre :as log])
  (:import [com.mongodb MongoClient MongoOptions MongoClientOptions
            ServerAddress WriteConcern]
           [org.bson.types ObjectId])
  (:gen-class))

(declare conn)
(declare db)

(def host "localhost")
(def port 27017)
(def db "DataKind")

(defn get-db-by-name [dbname]
  (cond (= dbname :db) db))

(defn mongo-shutdown
  "Disconnect from MongoDB." []
  (log/info "Disconnecting from MongoDB.")
  (m/disconnect conn))

(defn db-setup
  "Create database instance objects." [host port db user pwd]
  (let [^MongoClientOptions opts (m/mongo-options
                                  {:threads-allowed-to-block-for-connection-multiplier 300})
        ^ServerAddress sa        (m/server-address host port)
        ^MongoCredential cred    (mcr/create user "admin" pwd)]
    (log/info "Connecting to MongoDB.")
    (def conn (m/connect sa opts cred))
    (m/set-default-write-concern! WriteConcern/JOURNAL_SAFE)
    (def db (m/get-db conn db))))

(defn get-collection-names
  "Returns a set of all collection names."
    [& {:keys [dbname] :or {dbname :db}}]
    (let [db (get-db-by-name dbname)]
      (mdb/get-collection-names db)))

;;; Data insertion.

(defn put-map
  "Given a collection, insert a map as a document."
  [col m]
  (res/acknowledged? (col/insert db col m)))

(defn put-maps
  "Given a collection, insert a vector of maps."
  [col v & {:keys [dbname] :or {dbname :db}}]
  (when (seq v)
    (let [db (get-db-by-name dbname)]
      (res/acknowledged? (col/insert-batch
                          db col v)))))

;;; Data retrieval.

(defn get-map
  "Get a document from a collection, given some criteria.
  e.g. (get-map \"person\" {:nameFirst \"JOHN\"})"
  [col m & {:keys [dbname] :or {dbname :db}}]
  (let [db (get-db-by-name dbname)]
    (col (col/find-one-as-map db col m))))

(defn get-maps
  "Get multiple documents from a collection."
  [col & {:keys [conditions projection sort dbname page per-page]
          :or {conditions {} projection [] sort {} dbname :db page 0 per-page 0}}]
  (let [db (get-db-by-name dbname)]
    (q/with-collection db (name col)
           (q/find conditions)
           (q/fields (or projection []))
           (q/sort sort)
           (q/paginate :page page :per-page per-page))))

(defn get-by-id
  "Get a document, specified by _id (either as an ObjectId or string)."
  ([col id & {:keys [dbname] :or {dbname :db}}]
   (let [id (if (= ObjectId (class id))
              id (ObjectId. id))
         db (get-db-by-name dbname)]
     (col (col/find-one-as-map db col {:_id id})))))

;;; Deleting data

(defn drop-col
  "Drop collection from database."
  ([col & {:keys [dbname] :or {dbname :db}}]
   (let [db (get-db-by-name dbname)]
     (col/drop db col))))

(defn del-map
  "Delete a single document from a collection, specified by _id (either as an
  ObjectId or string).  Returns true if a record was found and deleted."
  ([col id & {:keys [dbname] :or {dbname :db}}]
   (let [id (if (= ObjectId (class id))
              id (ObjectId. id))
         db (get-db-by-name dbname)]
     (= 1 (.getN (col/remove-by-id db col id))))))

(defn del-maps
  "Delete multiple documents from a collection.  Returns true if one or more
  documents were deleted.  Optional conditions argument is a map of
  MongoDB-compatible conditions.
  e.g. (del-maps unit {:category \"JOINT\"})

  WARNING: Not specifying a conditions parameter will delete ALL documents from
  the collection."
  ([col] (pos? (.getN (col/remove db col))))
  ([col conditions] (pos? (.getN (col/remove db col conditions)))))

;;; Data updating.

(defn reset-by-id
  [col id]
  {:pre [(or (= (type id) ObjectId) (= (type id) String))]}
  (let [id (if (= ObjectId (class id)) id (ObjectId. id))]
    (col/update db col {:_id id} {})))

;; Custom querying.

(defn get-distinct-values
  "Get the distinct values for key from col."
  ([col key query & {:keys [dbname] :or {dbname :db}}]
   (let [db (get-db-by-name dbname)]
     (col/distinct db col key query))))

;; Metadata

(defn get-count
  "Get the number of rows in a collection."
  [col & {:keys [dbname conditions] :or {dbname :db conditions {}}}]
  (let [db (get-db-by-name dbname)]
    (col/count db col conditions)))

(defn drop-db-by-name
  [db-name]
  (m/drop-db conn db-name))
