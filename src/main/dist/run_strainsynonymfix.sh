# A program to replace "||" and "," separators into ; at Aliases table for old_strain_symbol
# "||" sign at the query of Statement stmt needs to be changed into "," for the second run 
# Created by : Burcu Bakir 
# Date: 04/27/2009
# Modified by Marek Tutaj, 3/12/2012 -- upgraded to latest rgdcore and jars
#
. /etc/profile
APPNAME=StrainSynonymFix

APPDIR=/home/rgddata/pipelines/$APPNAME
cd $APPDIR
./_run.sh