#!/usr/bin/env bash
# run the StrainSynonymFix pipeline
#
. /etc/profile
APPNAME=StrainSynonymFix

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/$APPNAME.jar "$@"