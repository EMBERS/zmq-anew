(ns zmq-anew3l.core
  (:gen-class)
  (:require [clojure.string           :as s]
            [clojure.java.io          :as io]
            [cheshire.core            :as json]
            [clojure.tools.cli        :as cli]
            [clojure.tools.logging    :as log]
            [wujuko-common.core       :as wc]
            [sentimental.anew         :as anew])
  (:import  [edu.embers.etool QueueConf Queue]
  	    [org.zeromq ZMQ]))

;; This is a resource packaged in the sentimental-data dependency.
;; The ANEW resource can be overridden on the command line.
(def anew-resource-file "3lang-an.csv")

;; Simple message counter
(def message-count (atom (long 0)))
(def ^:dynamic *count-log-interval* 500)

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
    (let [anew-en (anew/score-phrase (anew/string-or-seq text) :english)
          anew-es (anew/score-phrase (anew/string-or-seq text) :spanish)
          anew-pt (anew/score-phrase (anew/string-or-seq text) :portuguese)]
      (when-let [fin (filter (fn [x] (not (empty? (:words (second x)))))
                             {:eng anew-en
                              :spa anew-es
                              :por anew-pt})]
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

(defn get-value 
  "Get the value of an expression using path.  The datum can be a map
  or an iterable thing. E.g. t is
  {:tweet 
     {:items [
              {:value \"a\"} 
              {:value \"b\"} 
              {:value \"c\"} 
              {:value \"d\"} 
             ]
     }
     :thing \"a thing\"
  }
  (get-value t [:tweet :items :value]) 
     -> (\"a\" \"b\" \"c\" \"d\")
  (get-value t [:tweet :thing])
     -> \"a thing\" 
  This is a more general version of (reduce get datum path) that copes 
  with sequences/vectors.
  "
  [datum path]
  (reduce (fn [d k]
            (cond (map? d) 
                  (get d k)
                  (or (seq? d) (vector? d)) 
                  (map #(get % k) d)
                  true
                  d))
          datum path))


(defn score-stream
  "Very basic ZMQ synchronous subscription based ANEW scoring
   function."
  [pub sub anew-fn text-field feed-name]
    ;; Right now, this loops forever. Hooking up response to CTRL-C /
    ;; sig-KILL is next.
    ;; old, save for debugging (let [outs (java.io.PrintStream. System/out true "UTF-8")]
  (loop [item (.read sub)] 
        (do
	 (try
	  (let [jmsg        (json/parse-string item true)
	       text        (get-value jmsg text-field)
	       anew-b      (anew-fn text)
	       jdsrc       (if anew-b 
			       (assoc jmsg 
				      :anew anew-b
				      :feed feed-name) 
			       (assoc jmsg
                                    :feed feed-name))
	       linemessage (json/generate-string jdsrc)]
	       (if jmsg ;; exception evals this block
		   (do
		    (swap! message-count inc)
		    (.write pub linemessage)
		    ;;(. outs println linemessage)
		    (when (== 0 (rem @message-count *count-log-interval*))
		      (log/debug (str @message-count
				      " messages emitted."))))))
	  (catch Exception e (log/error e)))
          (recur (.read sub)))))


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
		 ["-pub"     "ZMQ output queue specification"
		  :default nil]
		 ["-sub"     "ZMQ input queue specification"
		  :default nil]
		 ["-service"     "Service name"
		  :default (.get (System/getenv) "UPSTART_JOB")]
                 ["-fn" "The scoring function one of 'bo3', 'all'"
                  :default :all
                  :parse-fn #(keyword %)]
                 ["-clog" "The message count logging interval"
                  :default 1000
                  :parse-fn wc/parse-number]
                 ["-field" "The JSON field to obtain text to score."
                  :default [:twitter :text]
                  :parse-fn parse-field]
                 ["-z" "--help" "Show help." :flag true]
                 ["-qconf" "Queue configuration file."])]

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

    (log/info (format "starting with pub=%s sub=%s service=%s fn=%s field=%s" 
                       (:pub opts) (:sub opts) (:service opts)
                       (:fn opts) (vec (:field opts))))

    ;; This is a "loop forever" process ...
    (let [qconf (QueueConf/locate (:qconf opts))
	 pub   (Queue/open (:pub opts) ZMQ/PUB (:service opts) qconf (.getClass ""))
	 sub   (Queue/open (:sub opts) ZMQ/SUB (:service opts) qconf (.getClass ""))]

	 (log/info "Starting stream score.")
	 (score-stream pub sub
		       ((:fn opts) anew-fns) (:field opts) (:service opts)))))
