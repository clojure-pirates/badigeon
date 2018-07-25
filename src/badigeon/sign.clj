(ns badigeon.sign
  (:require [clojure.java.io :as io]
            [badigeon.utils :as utils])
  (:import [java.nio.file Path]))

;; Taken from leiningen

(defn- get-english-env
  "Returns env vars as a map with clojure keywords and LANGUAGE set to 'en'"
  []
  (let [env (System/getenv)]
    (assoc (zipmap (map keyword (keys env)) (vals env))
           :LANGUAGE "en")))

(defn- as-env-strings
  [env]
  (into-array String (map (fn [[k v]] (str (name k) "=" v)) env)))

(defn signing-args [file gpg-key]
  (let [key-spec (when gpg-key
                   ["--default-key" gpg-key])]
    `["--yes" "-ab" ~@key-spec "--" ~file]))

(defn sign-one
  "Sign a single artifact. The artifact must be a map with a :file-path key and an optional :extension key. :file-path is the path to th file to be signed. :extension is the artifact packaging type. :extension is optional and defaults to \"jar\" for jar files and \"pom\" for pom files.
  Returns an artifact representing the signature of the input artifact."
  ([artifact]
   (sign-one artifact nil))
  ([artifact {:keys [command gpg-key] :or {command "gpg"}}]
   (let [{:keys [file-path extension]} (utils/artifact-with-default-extension artifact)
         file-path (str file-path)
         proc-env (as-env-strings (get-english-env))
         proc-args (into [command] (signing-args file-path gpg-key))
         proc (.exec (Runtime/getRuntime)
                     ^"[Ljava.lang.String;" (into-array String proc-args)
                     ^"[Ljava.lang.String;" proc-env)]
     (.addShutdownHook (Runtime/getRuntime)
                       (Thread. (fn [] (.destroy proc))))
     (with-open [proc-out (.getInputStream proc)
                 proc-err (.getErrorStream proc)]
       (let [exit-code (.waitFor proc)]
         (print (slurp (io/reader proc-out)))
         (print (slurp (io/reader proc-err)))
         (when (not= exit-code 0)
           (throw (ex-info "Error while signing"
                           {:exit-code exit-code
                            :file-path file-path
                            :command command
                            :proc-args proc-args})))))
     `{:file-path ~(str file-path ".asc")
       :badigeon/signature? true
       ~@(when extension [:extension (str extension ".asc")])
       ~@nil})))

(defn sign
  "Sign a collection of artifacts using the \"gpg\" command.
  - artifacts: A collections of artifacts. Each artifact must be a map with a :file-path key and an optional :extension key. :file-path is the path to th file to be signed. :extension is the artifact packaging type. :extension is optional and defaults to \"jar\" for jar files and \"pom\" for pom files.
  - command: The command used to sign the artifact. Default to \"gpg\".
  - gpg-key: The private key to be used. Default to the first private key found.
  Returns the artifacts representing the signatures of the input artifacts conjoined to the input artifacts."
  ([artifacts]
   (sign artifacts nil))
  ([artifacts {:keys [command gpg-key] :as opts}]
   (reduce #(conj %1 (sign-one %2 opts)) artifacts artifacts)))

