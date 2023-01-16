# A program to replace "||" and "," separators into ; at Aliases table for old_strain_symbol
# "||" sign at the query of Statement stmt needs to be changed into "," for the second run 
# Created by : Burcu Bakir 
# Date: 04/27/2009
#
. /etc/profile
APPNAME=StrainSynonymFix
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`

cd $APPDIR
./_run.sh

mailx -s "[$SERVER] Strain Synonym Fix pipeline OK" mtutaj@mcw.edu < $APPDIR/logs/summary.log
