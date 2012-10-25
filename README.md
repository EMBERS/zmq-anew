# zmq-anew

A clojure command line service designed to agument a Twitter stream
accessed via ZMQ with ANEW sentiment analysis. 

## Usage

Command line parameters can be discovered by running:

```
lein zmqanew -z
```

The resulting help output will look something like this:

```
Usage:

 Switches               Default           Desc                                             
 --------               -------           ----                                             
 -lexicon                                 ANEW lexicon CSV (overrides packaged dependency) 
 -host                  localhost         ZMQ Raw/Twitter Host                             
 -port                  30104             ZMQ Raw/Twitter Port                             
 -pport                 31338             ZMQ Publishing Port                              
 -fn                    :bo3              The scoring function one of 'bo3', 'all'         
 -clog                  10000             The message count logging interval               
 -field                 [:twitter :text]  The JSON field to obtain text to score.          
 -z, --no-help, --help  false             Show help. 
```

As we can see, many sensible defaults are provided.

## Command line parameters explained

### '-field' JSON text field

The field to examine in the source stream is specified in dot
notation. By default the path 'twitter.text' is examined. 

Given JSON structure:

```js
{"some": {"sub":
    { "twitter":{"text": "this is the text we want."} }
 }}
```

We would specify the parse field in dotted notation:

```
lein zmqanew -field some.sub.twitter.text
```

### '-fn' Scoring function 

Currently there are two output modes. 

1. Best of 3
2. All Languages

Best of three will take the best match of the three (one per language)
that match the text element in the source data. All will provide
separate language scores for each language.

```
lein zmqanew -fn bo3
```

The messages (excluding the original message) emitted look like:

```clojure
   {:v 5.71999979019165, :d 6.159999847412109, :a 4.380000114440918,
     :anew-es {:words [gato],
               :score {:v 5.71999979019165, :d 6.159999847412109,
                       :a 4.380000114440918}},
     :anew-pt {:words [gato],
               :score {:v 5.71999979019165, :d 6.159999847412109,
                       :a 4.380000114440918}}}
```

```
lein zmqanew -fn all
```

When providing the _all_ function, the emitted messages are structured
like:

```clojure
   {:anew-es {:words [casa],
              :score {:v 7.909999847412109, :d 5.900000095367432,
                      :a 4.210000038146973}},
    :anew-pt {:words [casa],
              :score {:v 7.909999847412109, :d 5.900000095367432,
                      :a 4.210000038146973}}}
```

The messages are of course converted to a JSON string and attached to
the original incoming message under the _anew_ map entry.

### '-lexicon' ANEW Lexicon 

By default, zmq-anew looks for the lexicon that is contained in one of
the dependencies _sentimental-data_. If for some reason you want to
provide an alternate dictionary, the command line option _-lexicon_
can be used.

```
lein zmqanew -lexicon /full/path/to/lexicon.csv
```

## License

Distributed under the Eclipse Public License, the same as Clojure.
