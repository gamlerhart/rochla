#!/usr/bin/env bash
INSTALL_DIR=$(cd `dirname $0` && pwd)/../

mkdir $INSTALL_DIR"/logs"
GC_LOG=$INSTALL_DIR"/logs/gc.log"
HOST=`hostname`
SYS_OUT=$INSTALL_DIR"/logs/sysout.log"
JAR_FILE=$INSTALL_DIR"/rochla.jar"

JAVA_ARGS="-Xmx128m -XX:+UseParallelOldGC -Dfile.encoding=UTF-8 -Duser.timezone=UTC -Dlogback.configurationFile=$INSTALL_DIR/logback.xml -verbose:gc -Xloggc:"$GC_LOG" -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintTenuringDistribution -XX:+HeapDumpOnOutOfMemoryError"


cd $INSTALL_DIR
java $JAVA_ARGS -jar $JAR_FILE

