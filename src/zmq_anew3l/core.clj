(ns zmq-anew3l.core
;;  (:gen-class)
  (:require [zmq-anew3l.zmqex.zhelper :as mq]
            [clojure.string           :as s]
            [clojure.java.io          :as io]
            [cheshire.core            :as json]
            [clojure.tools.cli        :as cli]
            [clojure.tools.logging    :as log]
            [wujuko-common.core       :as wc]
            [sentimental.anew         :as anew]))

;; This is a resource packaged in the sentimental-data dependency.
;; The ANEW resource can be overridden on the command line.
(def anew-resource-file "3lang-an.csv")

;; Simple message counter
(def message-count (atom (long 0)))
(def ^:dynamic *count-log-interval* 500)

(defn queue-address [host port] (str "tcp://" host ":" port))

(defn gen-all 
  "Takes a JSON (Clojure) input, and produces JSON (Clojure) output.

   nil is returned if there is no score.

   Emits structure like:

   {:anew-es {:words [casa],
              :score {:v 7.909999847412109, :d 5.900000095367432,
                      :a 4.210000038146973}},
    :anew-pt {:words [casa],
              :score {:v 7.909999847412109, :d 5.900000095367432,
                      :a 4.210000038146973}}}"
  [text]
  (when text
    (let [anew-en (anew/score-phrase text :english)
          anew-es (anew/score-phrase text :spanish)
          anew-pt (anew/score-phrase text :portuguese)]
      (when-let [fin (filter (fn [x] (not (empty? (:words (second x)))))
                             {:anew-en anew-en
                              :anew-es anew-es
                              :anew-pt anew-pt})]
        (if (not (empty? fin)) (into {} fin))))))

(defn gen-best-of3
  "Provides the 'Best of the 3' -- the score that matches the most keywords.

   Emits structure like:

   {:v 5.71999979019165, :d 6.159999847412109, :a 4.380000114440918,
     :anew-es {:words [gato],
               :score {:v 5.71999979019165, :d 6.159999847412109,
                       :a 4.380000114440918}},
     :anew-pt {:words [gato],
               :score {:v 5.71999979019165, :d 6.159999847412109,
                       :a 4.380000114440918}}}
  "
  [text]
  (when text
    (anew/score-phrase-langs text)))

;;
;; A map of our score-generator functions, we can swap these out with
;; a command line param / lookup.
(def anew-fns {:bo3 gen-best-of3 :all gen-all})


(defn score-stream
  "Very basic ZMQ synchronous subscription based ANEW scoring
   function."
  [host port pport anew-fn text-field]
  (let [ctx        (mq/context 1)
        subscriber (mq/socket ctx mq/sub)
        publisher  (mq/socket ctx mq/pub)]
    ;; Bind the publisher
    (mq/bind publisher (queue-address "*" pport))
    ;; Connect the subscriber
    (mq/connect subscriber (queue-address host port))
    (mq/subscribe subscriber "")
    (mq/recv subscriber)

    ;; Right now, this loops forever. Hooking up response to CTRL-C /
    ;; sig-KILL is next.
    (loop [item (mq/recv-str subscriber)]
      (do
        (let [jmsg     (json/parse-string item true)
              text     (reduce get jmsg text-field)
              anew-b   (anew-fn text)]
          (when anew-b
            (let [linemessage (json/generate-string (assoc jmsg :anew anew-b))]
              (swap! message-count inc)
              (mq/send publisher linemessage)
              (when (== 0 (rem @message-count *count-log-interval*))
                (log/info (str @message-count
                               " messages emitted with ANEW agumentation"))))))
        (recur (mq/recv-str subscriber))))))


(defn parse-field
  "Takes a field in 'dot' notataion and returns an array of fields for use in
   reduce:

   Given JSON structure:

   {\"some\": {\"sub\":
       { \"twitter\":{\"text\": \"this is the text we wknt.\"}
          ...}
    }
   }

   Parse field in dotted notation:

   \"some.sub.twitter.text\"

   Gives us: [:some :sub :twitter :text]

   Which is now usable like:

   (reduce get tmap (parse-field \"some.sub.twitter.text\"))
  "
  [sf]
  (map keyword (s/split sf #"\.")))


;;
;; Main
(defn -main
  "Compiles down to public static void main(String[] args) for JVM entry point
   from command line."
  [& args]
  (let [[opts args banner]
        (cli/cli args
                 ["-lexicon" "ANEW lexicon CSV (overrides packaged dependency)"]
                 ["-host"    "ZMQ Raw/Twitter Host"
                  :default "localhost"]
                 ["-port"    "ZMQ Raw/Twitter Port"
                  :default "30104"]
                 ["-pport"   "ZMQ Publishing Port"
                  :default "31338"]
                 ["-fn" "The scoring function one of 'bo3', 'all'"
                  :default :bo3
                  :parse-fn #(keyword %)]
                 ["-clog" "The message count logging interval"
                  :default 500 
                  :parse-fn wc/parse-number]
                 ["-field" "The JSON field to obtain text to score."
                  :default [:twitter :text]
                  :parse-fn parse-field]
                 ["-z" "--help" "Show help." :flag true])]

    ;; If the required options are not present from the command line
    ;; print the banner and exit.
    (when (or (:help opts))
      (println banner)
      (System/exit 0))

    ;; Rebind the count log interval
    (def ^:dynamic *count-log-interval* (:clog opts))

    ;; Load the ANEW lexicon. Future version can read directly from S3.
    ;; (expect as a paramter a filename or URL)
    (log/info "Loading Lexicon...")
    (anew/load-lexicon
     (or (:lexicon opts)
         (io/resource anew-resource-file)))

    ;; This is a "loop forever" process ...
    (log/info "Starting stream score.")
    (score-stream (:host opts) (:port opts) (:pport opts)
                  ((:fn opts) anew-fns) (:field opts))))
