#!/bin/bash

# Very simple (and non-sophisticated) 'deployment'

ddir=DESTINATIONDIR
dhost=DESTINATIONHOST
dest=$dhost:$ddir


lein clean ; lein deps ; lein compile ; lein uberjar
uberjar=`ls -1 target/*-standalone.jar`

ssh $dhost "rm -rf $ddir/backup"
ssh $dhost "mkdir $ddir/backup"
ssh $dhost "mv $ddir/*-standalone.jar $ddir/backup"
ssh $dhost "mv $ddir/*.sh $ddir/backup"
scp bin/*.sh $dest
scp $uberjar $dest
