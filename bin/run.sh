#!/bin/bash

APPNAME="zmq-anew3l"
JAVA=`which java`
JAVAOPTS="-Xmx1024m -server -XX:MaxPermSize=256m -Djava.library.path=/usr/local/lib/"

# 
#app_home=/home/ubuntu/prod/zmq-anew
#cd $app_home
jjar=`ls -1 *-standalone.jar`


set_basename() {
    regex="([A-Za-z-]+[A-Za-z0-9]+[A-Za-z-]+)"
    
    [[ $jjar =~ $regex ]]
    if [ "${BASH_REMATCH[1]}" != "" ]; then
        BASENAME=${BASH_REMATCH[1]}
    fi

    if [ "$BASENAME" == "" ]; then
        echo "Could not identify basename $jjar"
        echo "Exiting (rc 5)."
        exit 5
    else
        echo "Setting BASENAME to: $BASENAME"
    fi
}

set_version() {
    if [ "$1" == "" ]; then
        vnum="[0-9]+\.[0-9]+\.[0-9]+"
        regex="($vnum-SNAPSHOT)|($vnum)"
        
        [[ $jjar =~ $regex ]]
        if [ "${BASH_REMATCH[1]}" != "" ]; then
            VERSION=${BASH_REMATCH[1]}
        elif [ "${BASH_REMATCH[2]}" != "" ]; then
            VERSION=${BASH_REMATCH[2]}
        fi
        
        if [ "$VERSION" == "" ]; then
            echo "Could not identify version $jjar"
            echo "Exiting (rc 5)."
            exit 5
        else
            echo "Setting VERSION to: $VERSION"
        fi
    else
        VERSION=$1
        echo "Manually setting VERSION to: $VERSION"
    fi
            
    JARNAME="$BASENAME$VERSION-standalone.jar"
    echo "Setting Constructed JARNAME to: $JARNAME"
}

pid_rv=0
get_pid() { 
    pid_rv=`ps aux | grep "$1" | grep -v grep | head -n 1 | awk '{print $2}'`
}

show_usage() {
    echo ""
    echo "run.sh [start|stop|status] version"
    echo "   version defaults to "$VERSION
    echo "   jarname defaults to "$JARNAME
    echo ""
}

get_gfpids() {
    get_pid $JARNAME
    if [ "$pid_rv" != "" ]; then
        webapppid=$pid_rv
    fi
}

app_status() {
    get_gfpids
    echo ""
    echo "Process ID for ($APPNAME $VERSION) "$webapppid:
    echo ""
}

set_basename
set_version $2

#
# What are we doing? Check the command!
#
cmd=$1
if [ "$cmd" == "" ]; then
    show_usage
    echo ""
    cmd="status"
fi

if [ "$cmd" == "stop" ]; then
    app_status
    echo "Request to STOP"
    echo "Sending kill request."
    kill $webapppid
    echo "Sleeping"
    sleep 10
    app_status
elif [ "$cmd" == "start" ]; then
    echo "Request to START"
    echo "Executing Runner with Command:"
    echo "$JAVA $JAVAOPTS -jar $JARNAME > /dev/null 2>&1 &" 
    nohup $JAVA $JAVAOPTS -jar $JARNAME > /dev/null 2>&1 &
    sleep 10
    app_status
elif [ "$cmd" == "status" ]; then
    app_status
fi
