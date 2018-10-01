#!/usr/bin/env bash
# run the StrainSynonymFix pipeline
#
. /etc/profile
APPNAME=StrainSynonymFix

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

DB_OPTS="-Dspring.config=$APPDIR/../properties/default_db.xml"
LOG4J_OPTS="-Dlog4j.configuration=file://$APPDIR/properties/log4j.properties"
declare -x "STRAIN_SYNONYM_FIX_OPTS=$DB_OPTS $LOG4J_OPTS"
bin/$APPNAME "$@"