# Transfer strain descriptions, synonyms and xrefs from strain objects to RS ontology
#
. /etc/profile
APPNAME="strain-synonym-fix-pipeline"
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

cd $APPDIR
./_run.sh --update_ontology_from_strains > ${APPDIR}/update.log

mailx -s "[$SERVER] Strain to RS ontology pipeline OK" mtutaj@mcw.edu < $APPDIR/logs/summary.log
